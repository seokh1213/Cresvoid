import java.util.SortedMap

class TransactionEngine(private val output: Output) {
    private val store = VersionStore()
    private val locks = LockManager()
    private val transactions = HashMap<String, Transaction>()

    fun handle(line: String) {
        when (val parsed = parseLine(line)) {
            is ParsedLine.Invalid -> output.syntaxError(parsed.error)
            is ParsedLine.Valid -> when (val command = parsed.command) {
                is Command.Begin -> begin(command)
                is Command.Statement -> accept(command)
            }
        }
    }

    private fun begin(command: Command.Begin) {
        if (command.transaction in transactions) return output.stateError(command.transaction, StateError.EXISTS)
        val startOrder = transactions.size
        val transaction = Transaction(command.transaction, startOrder, command.isolation, store.currentSnapshot())
        transactions[command.transaction] = transaction
        output.begun(command.transaction)
    }

    private fun accept(statement: Command.Statement) {
        val transaction = transactions[statement.transaction]
        when {
            transaction == null -> output.stateError(statement.transaction, StateError.NO_TX)
            transaction.isWaiting -> transaction.queue.addLast(statement)
            else -> {
                execute(transaction, statement)
                resume()
            }
        }
    }

    private fun execute(transaction: Transaction, statement: Command.Statement) {
        if (transaction.status != TransactionStatus.ACTIVE) return output.stateError(transaction.name, StateError.ENDED)
        val request = statement.lockRequest()
        if (request == null || locks.tryAcquire(transaction, request)) {
            run(transaction, statement)
        } else {
            waitForLock(transaction, statement, request)
        }
    }

    private fun waitForLock(transaction: Transaction, statement: Command.Statement, request: LockRequest) {
        val holders = locks.conflictingHolders(transaction, request).sortedBy { it.startOrder }
        output.waiting(transaction.name, holders.map { it.name })
        transaction.startWaiting(statement)
        locks.enqueue(transaction, request)
        breakDeadlocks(transaction)
    }

    // 고리는 기다리기 시작하는 순간에만 생기므로 새로 기다리는 트랜잭션을 지나는 고리만 찾으면 된다
    private fun breakDeadlocks(waiter: Transaction) {
        while (waiter.isWaiting) {
            val deadlocked = transactionsInCycleWith(waiter, locks::blockersOf)
            if (deadlocked.isEmpty()) return
            abortDeadlockVictim(deadlocked.maxBy { it.startOrder })
        }
    }

    private fun abortDeadlockVictim(victim: Transaction) {
        output.deadlockVictim(victim.name)
        abort(victim)
        runQueue(victim)
    }

    private fun resume() {
        while (true) {
            val transaction = locks.grantNext() ?: return
            run(transaction, transaction.stopWaiting())
            runQueue(transaction)
        }
    }

    private fun runQueue(transaction: Transaction) {
        while (!transaction.isWaiting) {
            val statement = transaction.queue.removeFirstOrNull() ?: return
            execute(transaction, statement)
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
            abort(transaction)
            output.writeConflict(transaction.name, conflictingKey)
            return
        }
        store.commit(transaction.writes)
        transaction.markCommitted()
        locks.releaseAll(transaction)
        output.committed(transaction.name)
    }

    private fun firstConflictingKey(transaction: Transaction): String? {
        if (transaction.isolation == IsolationLevel.READ_COMMITTED) return null
        return transaction.writes.keys.firstOrNull { store.hasVersionOutside(it, transaction.beginSnapshot) }
    }

    private fun rollback(transaction: Transaction) {
        abort(transaction)
        output.rolledBack(transaction.name)
    }

    private fun abort(transaction: Transaction) {
        transaction.markAborted()
        locks.releaseAll(transaction)
    }
}
