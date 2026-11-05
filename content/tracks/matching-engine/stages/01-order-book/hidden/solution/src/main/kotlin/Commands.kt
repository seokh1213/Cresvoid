enum class Side { BUY, SELL }

sealed interface Command {
    sealed interface Mutation : Command
    data class Submit(val request: OrderRequest) : Mutation
    data class Cancel(val id: String) : Mutation
    data object Book : Command
}

data class OrderRequest(
    val id: String,
    val owner: String,
    val side: Side,
    val price: Long,
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

class Rejected(val reason: String) : RuntimeException(null, null, false, false)
