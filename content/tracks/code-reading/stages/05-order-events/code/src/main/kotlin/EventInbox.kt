data class EventSelection(
    val events: List<OrderEvent>,
    val first: EventKey?,
    val readTrace: List<EventKey>
)

class InboxSnapshot(private val arrivals: List<OrderEvent>) {
    fun select(cutoff: Long): EventSelection {
        val readTrace = mutableListOf<EventKey>()
        val eligible = arrivals.asSequence()
            .onEach { readTrace += it.key }
            .filter { it.occurredAt <= cutoff }
            .distinctBy { it.key }
        val first = eligible.firstOrNull()
            ?: return EventSelection(emptyList(), null, readTrace.toList())
        val events = eligible.toList()
        return EventSelection(events, first.key, readTrace.toList())
    }

    fun receipts(orderId: String): String {
        val deliveries = arrivals.filter { it.orderId == orderId }
        val canonical = deliveries.distinctBy { it.key }.sortedBy { it.sequence }
        val latest = deliveries.associateBy { it.key }.values.sortedBy { it.sequence }
        return listOf(
            orderId,
            "canonical=${canonical.receiptList()}",
            "latest=${latest.receiptList()}",
            "deliveries=${deliveries.size}"
        ).joinToString("|")
    }

    private fun List<OrderEvent>.receiptList(): String =
        joinToString(",") { it.receipt() }.ifEmpty { "-" }
}

class EventInbox {
    private val arrivals = mutableListOf<OrderEvent>()

    fun append(event: OrderEvent) {
        arrivals += event
    }

    fun snapshot(): InboxSnapshot = InboxSnapshot(arrivals.toList())
}

data class Projection(
    val orders: List<OrderReplay>,
    val selection: EventSelection
) {
    fun order(orderId: String): OrderReplay = orders.firstOrNull { it.orderId == orderId }
        ?: OrderReplay(
            orderId = orderId,
            state = OrderState.AwaitingPayment,
            applied = emptyList(),
            rejected = emptyList(),
            nextSequence = 1,
            pending = 0
        )

    fun totals(): String {
        val applied = orders.sumOf { it.applied.size }
        val rejected = orders.sumOf { it.rejected.size }
        val pending = orders.sumOf { it.pending }
        return "orders=${orders.size},applied=$applied,rejected=$rejected,pending=$pending"
    }
}
