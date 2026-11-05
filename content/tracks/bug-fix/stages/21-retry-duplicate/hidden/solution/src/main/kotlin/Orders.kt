enum class OrderStatus { NEW, PAID, PACKED, SHIPPED, CANCELLED }

data class Order(
    val id: String,
    val status: OrderStatus,
    val changes: Int = 0,
) {
    fun moveTo(nextStatus: OrderStatus): Order =
        copy(status = nextStatus, changes = changes + 1)

    fun describe(): String = "ORDER $id ${status.name} changes=$changes"
}

class Orders(private val publisher: HistoryPublisher) {
    private val registered = mutableMapOf<String, Order>()

    fun open(orderId: String, status: OrderStatus): String {
        val existing = registered[orderId]
        if (existing != null) return "EXISTS $orderId ${existing.status.name}"
        registered[orderId] = Order(orderId, status)
        return "OPENED $orderId ${status.name}"
    }

    fun change(orderId: String, nextStatus: OrderStatus): String {
        val previous = registered[orderId] ?: return "NOT_FOUND $orderId"
        if (previous.status == nextStatus) {
            return "UNCHANGED $orderId ${nextStatus.name}"
        }
        val change = StatusChange(orderId, previous.status, nextStatus)
        registered[orderId] = previous.moveTo(nextStatus)
        val delivery = publisher.publish(change)
        return "CHANGED $orderId ${change.describe()} ${delivery.describe()}"
    }

    fun show(orderId: String): String =
        registered[orderId]?.describe() ?: "NOT_FOUND $orderId"
}
