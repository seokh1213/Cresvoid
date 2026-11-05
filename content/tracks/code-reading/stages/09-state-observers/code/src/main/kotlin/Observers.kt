interface OrderObserver {
    val name: String
    fun onChange(order: Order, change: OrderChange)
}

class NotificationJournal {
    private val entries = mutableListOf<String>()

    fun record(observer: OrderObserver, order: Order, change: OrderChange) {
        entries.add(
            "${observer.name}:${change.orderId}#${change.version}" +
                ":${change.before.code}>${change.after.code}@${order.state.code}"
        )
    }

    fun recordRejected(order: Order, action: OrderAction, outcome: String) {
        entries.add("$outcome:${order.id}:${action.name}@${order.state.code}")
    }

    fun render(): String = entries.joinToString(" | ").ifEmpty { "(empty)" }
}

class LedgerObserver(private val journal: NotificationJournal) : OrderObserver {
    override val name = "ledger"

    override fun onChange(order: Order, change: OrderChange) {
        journal.record(this, order, change)
    }
}

class DispatchObserver(private val journal: NotificationJournal) : OrderObserver {
    override val name = "dispatch"

    override fun onChange(order: Order, change: OrderChange) {
        journal.record(this, order, change)
        if (change.after === PaidState) {
            order.request(OrderRequest(OrderAction.SHIP, "auto-${order.id}"))
        }
    }
}

class ReceiptObserver(private val journal: NotificationJournal) : OrderObserver {
    override val name = "receipt"

    override fun onChange(order: Order, change: OrderChange) {
        journal.record(this, order, change)
        order.unsubscribe(this)
    }
}

class OrderRegistration(val order: Order, journal: NotificationJournal) {
    private val observers: Map<String, OrderObserver> = listOf(
        LedgerObserver(journal),
        DispatchObserver(journal),
        ReceiptObserver(journal)
    ).associateBy { it.name }

    fun observer(name: String): OrderObserver =
        requireNotNull(observers[name]) { "Unknown observer: $name" }
}
