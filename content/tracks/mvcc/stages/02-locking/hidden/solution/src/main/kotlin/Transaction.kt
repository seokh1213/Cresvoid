import java.util.TreeMap

enum class TransactionStatus {
    ACTIVE,
    COMMITTED,
    ABORTED,
}

class Transaction(
    val name: String,
    val startOrder: Int,
    val isolation: IsolationLevel,
    val beginSnapshot: Snapshot,
) {
    val writes = TreeMap<String, String>()
    val queue = ArrayDeque<Command.Statement>()
    private var waitingStatement: Command.Statement? = null

    var status = TransactionStatus.ACTIVE
        private set

    val isWaiting: Boolean
        get() = waitingStatement != null

    fun startWaiting(statement: Command.Statement) {
        waitingStatement = statement
    }

    fun stopWaiting(): Command.Statement = checkNotNull(waitingStatement).also { waitingStatement = null }

    fun markCommitted() = end(TransactionStatus.COMMITTED)

    fun markAborted() = end(TransactionStatus.ABORTED)

    private fun end(outcome: TransactionStatus) {
        status = outcome
        writes.clear()
        waitingStatement = null
    }
}
