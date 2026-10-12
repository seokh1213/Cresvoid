import java.util.TreeMap

enum class TransactionStatus {
    ACTIVE,
    COMMITTED,
    ABORTED,
}

class Transaction(val name: String, val isolation: IsolationLevel, val beginSnapshot: Snapshot) {
    val writes = TreeMap<String, String>()

    var status = TransactionStatus.ACTIVE
        private set

    fun markCommitted() = end(TransactionStatus.COMMITTED)

    fun markAborted() = end(TransactionStatus.ABORTED)

    private fun end(outcome: TransactionStatus) {
        status = outcome
        writes.clear()
    }
}
