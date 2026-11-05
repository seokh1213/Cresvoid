enum class Side { BUY, SELL }
enum class OrderKind { LIMIT, MARKET, IOC, FOK }

sealed interface Command {
    sealed interface Mutation : Command
    data class Submit(val request: OrderRequest) : Mutation
    data class Cancel(val id: String) : Mutation
    data class Amend(val id: String, val price: Long, val quantity: Long) : Mutation
    data object Book : Command
    data object Journal : Command
    data object Snapshot : Command
    data class Event(val sequence: Long, val body: List<String>) : Command
    data class Restore(val header: SnapshotHeader) : Command
}

data class OrderRequest(
    val id: String,
    val owner: String,
    val side: Side,
    val kind: OrderKind,
    val price: Long?,
    val quantity: Long,
)

data class Order(
    val id: String,
    val owner: String,
    val side: Side,
    val price: Long,
    var remaining: Long,
    val arrival: Long,
)

data class SnapshotHeader(
    val lastSequence: Long,
    val arrivalClock: Long,
    val tradeClock: Long,
    val usedCount: Int,
    val openCount: Int,
)

class Rejected(val reason: String) : RuntimeException(null, null, false, false)
