import java.util.SortedMap

class TransactionEngine(private val output: Output) {
    private val store = VersionStore()
    private val transactions = HashMap<String, Transaction>()

    fun handle(line: String) {
        when (val parsed = parseLine(line)) {
            is ParsedLine.Invalid -> output.syntaxError(parsed.error)
            is ParsedLine.Valid -> when (val command = parsed.command) {
                is Command.Begin -> begin(command)
                is Command.Statement -> execute(command)
            }
        }
    }

    private fun begin(command: Command.Begin) {
        if (command.transaction in transactions) return output.stateError(command.transaction, StateError.EXISTS)
        transactions[command.transaction] = Transaction(command.transaction, command.isolation, store.currentSnapshot())
        output.begun(command.transaction)
    }

    private fun execute(statement: Command.Statement) {
        val transaction = transactions[statement.transaction]
        when {
            transaction == null -> output.stateError(statement.transaction, StateError.NO_TX)
            transaction.status != TransactionStatus.ACTIVE -> output.stateError(statement.transaction, StateError.ENDED)
            else -> run(transaction, statement)
        }
    }

    private fun run(transaction: Transaction, statement: Command.Statement) {
        when (statement) {
            is Command.Read -> output.value(transaction.name, statement.key, visibleValue(transaction, statement.key))
            is Command.Write -> write(transaction, statement.key, statement.value)
            is Command.Scan -> output.scanned(transaction.name, visibleValues(transaction, statement.range))
            is Command.Commit -> commit(transaction)
            is Command.Rollback -> rollback(transaction)
        }
    }

    private fun snapshotFor(transaction: Transaction): Snapshot =
        if (transaction.isolation == IsolationLevel.READ_COMMITTED) store.currentSnapshot() else transaction.beginSnapshot

    private fun visibleValue(transaction: Transaction, key: String): String? =
        transaction.writes[key] ?: store.valueAt(key, snapshotFor(transaction))

    private fun visibleValues(transaction: Transaction, range: KeyRange): SortedMap<String, String> {
        val values = store.valuesAt(range, snapshotFor(transaction))
        values.putAll(transaction.writes.subMap(range.start, range.end))
        return values
    }

    private fun write(transaction: Transaction, key: String, value: String) {
        transaction.writes[key] = value
        output.written(transaction.name)
    }

    private fun commit(transaction: Transaction) {
        val conflictingKey = firstConflictingKey(transaction)
        if (conflictingKey != null) {
            transaction.markAborted()
            output.writeConflict(transaction.name, conflictingKey)
            return
        }
        store.commit(transaction.writes)
        transaction.markCommitted()
        output.committed(transaction.name)
    }

    private fun firstConflictingKey(transaction: Transaction): String? {
        if (transaction.isolation == IsolationLevel.READ_COMMITTED) return null
        return transaction.writes.keys.firstOrNull { store.hasVersionOutside(it, transaction.beginSnapshot) }
    }

    private fun rollback(transaction: Transaction) {
        transaction.markAborted()
        output.rolledBack(transaction.name)
    }
}
