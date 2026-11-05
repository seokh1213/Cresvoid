class Order(val id: String, private val rejectionPolicy: RejectionPolicy) {
    var state: OrderState = CreatedState
        private set
    var version: Int = 0
        private set
    var paymentReference: String? = null
        private set
    var shipmentReference: String? = null
        private set
    var cancellationReference: String? = null
        private set

    private val subscribers = LinkedHashSet<OrderObserver>()

    fun subscribe(observer: OrderObserver) {
        subscribers.add(observer)
    }

    fun unsubscribe(observer: OrderObserver) {
        subscribers.remove(observer)
    }

    fun subscriberNames(): String =
        subscribers.joinToString(",") { it.name }.ifEmpty { "-" }

    fun request(request: OrderRequest): Boolean {
        val before = state
        val after = before.next(request.action)
        if (after == null) {
            return reject(request.action)
        }
        recordReference(request)
        state = after
        version += 1
        val change = OrderChange(id, version, before, after)
        notifySubscribers(change)
        return true
    }

    private fun reject(action: OrderAction): Boolean {
        if (rejectionPolicy == RejectionPolicy.REJECT) {
            throw RejectedTransition(id, action, state.code)
        }
        return false
    }

    private fun recordReference(request: OrderRequest) {
        when (request.action) {
            OrderAction.PAY -> paymentReference = request.reference
            OrderAction.SHIP -> shipmentReference = request.reference
            OrderAction.CANCEL -> cancellationReference = request.reference
        }
    }

    private fun notifySubscribers(change: OrderChange) {
        val recipients = subscribers.toList()
        for (observer in recipients) {
            observer.onChange(this, change)
        }
    }

    fun summary(): String =
        "$id:${state.code}#$version" +
            ":pay=${paymentReference ?: "-"}" +
            ":ship=${shipmentReference ?: "-"}" +
            ":cancel=${cancellationReference ?: "-"}" +
            ":sub=${subscriberNames()}"
}
