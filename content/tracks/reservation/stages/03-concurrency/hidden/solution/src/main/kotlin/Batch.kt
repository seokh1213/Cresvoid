class Batch(private val mode: LockMode, private val hotel: Hotel) {
    private val transactions = linkedMapOf<String, Transaction>()
    private val reservationIds = mutableSetOf<String>()
    private val locks = mutableSetOf<InventoryRow>()
    private val versions = mutableMapOf<InventoryRow, Long>()
    val finished: Boolean get() = transactions.values.all { it.state.terminal }

    fun execute(command: Command.BatchOperation): String = when (command) {
        is Command.Tx -> register(command)
        is Command.Step -> step(command.id)
        Command.End -> end()
    }

    private fun register(command: Command.Tx): String {
        val booking = command.booking
        if (hotel.room(booking.type) == null) return "ERROR TYPE_NOT_FOUND"
        if (command.id in transactions) return "ERROR TX_EXISTS"
        if (hotel.hasReservation(booking.id) || booking.id in reservationIds) return "ERROR RESERVATION_EXISTS"
        transactions[command.id] = Transaction(command.id, booking)
        reservationIds.add(booking.id)
        return "OK TX ${command.id}"
    }

    private fun step(id: String): String {
        val transaction = transactions[id] ?: return "ERROR TX_NOT_FOUND"
        when (val state = transaction.state) {
            TransactionState.Ready -> prepare(transaction)
            is TransactionState.Prepared -> commit(transaction, state)
            TransactionState.Booked, TransactionState.Full -> Unit
        }
        return transaction.response("TX")
    }

    private fun prepare(transaction: Transaction) {
        if (mode == LockMode.PESSIMISTIC) {
            if (transaction.rows.any { it in locks }) {
                transaction.waits++
                return
            }
            locks.addAll(transaction.rows)
        }
        val room = checkNotNull(hotel.room(transaction.booking.type))
        val observations = transaction.rows.map { row ->
            Observation(row, room.snapshot(row.date), versions[row] ?: 0)
        }
        val acceptable = observations.all { it.stock.remaining >= transaction.booking.quantity }
        transaction.attempts++
        transaction.state = TransactionState.Prepared(observations, acceptable)
    }

    private fun commit(transaction: Transaction, prepared: TransactionState.Prepared) {
        when (mode) {
            LockMode.PESSIMISTIC -> {
                resolveRead(transaction, prepared)
                locks.removeAll(transaction.rows.toSet())
            }
            LockMode.OPTIMISTIC -> {
                if (prepared.observations.any { (versions[it.row] ?: 0) != it.version }) transaction.retry()
                else resolveRead(transaction, prepared)
            }
            LockMode.CONSTRAINT -> commitWithConstraint(transaction, prepared)
        }
    }

    private fun commitWithConstraint(transaction: Transaction, prepared: TransactionState.Prepared) {
        if (!prepared.acceptable) {
            transaction.state = TransactionState.Full
            return
        }
        val room = checkNotNull(hotel.room(transaction.booking.type))
        if (room.canBook(transaction.booking)) confirm(transaction)
        else transaction.retry()
    }

    private fun resolveRead(transaction: Transaction, prepared: TransactionState.Prepared) {
        if (prepared.acceptable) confirm(transaction)
        else transaction.state = TransactionState.Full
    }

    private fun confirm(transaction: Transaction) {
        hotel.confirm(transaction.booking)
        transaction.rows.forEach { versions[it] = (versions[it] ?: 0) + 1 }
        transaction.state = TransactionState.Booked
    }

    private fun end(): String {
        if (!finished) return "ERROR UNFINISHED"
        val completed = transactions.values
        val booked = completed.count { it.state == TransactionState.Booked }
        val full = completed.size - booked
        val summary = "SUMMARY $booked $full ${completed.sumOf { it.attempts }} " +
            "${completed.sumOf { it.retries }} ${completed.sumOf { it.waits }}"
        return (completed.map { it.response("RESULT") } + summary).joinToString("\n")
    }
}
