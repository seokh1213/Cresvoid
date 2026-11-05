enum class EventKind {
    PAY, SHIP, CANCEL, REFUND
}

data class EventKey(val orderId: String, val sequence: Int) {
    override fun toString(): String = "$orderId#$sequence"
}

data class OrderEvent(
    val key: EventKey,
    val kind: EventKind,
    val occurredAt: Long
) {
    val orderId: String get() = key.orderId
    val sequence: Int get() = key.sequence

    fun receipt(): String = "$sequence:${kind.name}@$occurredAt"
}

sealed interface OrderState {
    data object AwaitingPayment : OrderState
    data class Paid(val paidAt: Long) : OrderState
    data class Shipped(val paidAt: Long, val shippedAt: Long) : OrderState
    data class Cancelled(val cancelledAt: Long) : OrderState
    data class Refunded(val refundedAt: Long) : OrderState
}

fun OrderState.label(): String = when (this) {
    OrderState.AwaitingPayment -> "WAITING"
    is OrderState.Paid -> "PAID@$paidAt"
    is OrderState.Shipped -> "SHIPPED@$shippedAt"
    is OrderState.Cancelled -> "CANCELLED@$cancelledAt"
    is OrderState.Refunded -> "REFUNDED@$refundedAt"
}

sealed interface Transition {
    data class Applied(val state: OrderState) : Transition
    data class Rejected(val reason: String) : Transition
}

data class RejectedEvent(val sequence: Int, val reason: String) {
    override fun toString(): String = "$sequence:$reason"
}

data class OrderReplay(
    val orderId: String,
    val state: OrderState,
    val applied: List<Int>,
    val rejected: List<RejectedEvent>,
    val nextSequence: Int,
    val pending: Int
) {
    fun summary(): String = listOf(
        orderId,
        state.label(),
        "applied=${applied.joinToString(",").ifEmpty { "-" }}",
        "rejected=${rejected.joinToString(",").ifEmpty { "-" }}",
        "next=$nextSequence",
        "pending=$pending"
    ).joinToString("|")

    fun exportRecord(): OrderExport = OrderExport(
        orderId = orderId,
        state = state,
        consumedThrough = nextSequence - 1
    )
}

data class OrderExport(
    val orderId: String,
    val state: OrderState,
    val consumedThrough: Int
) {
    override fun toString(): String = "$orderId:${state.label()}:$consumedThrough"
}
