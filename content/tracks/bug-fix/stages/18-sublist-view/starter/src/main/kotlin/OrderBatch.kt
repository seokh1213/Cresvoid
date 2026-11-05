class OrderBatch {
    private val orders = linkedMapOf<String, Order>()
    private val pending = arrayListOf<String>()

    fun add(orderId: String, failures: Int): String {
        if (orderId in orders) return "ERROR duplicate-order"
        orders[orderId] = Order(orderId, failures)
        pending.add(orderId)
        return "ADDED $orderId"
    }

    fun show(orderId: String): String =
        orders[orderId]?.describe() ?: "ERROR unknown-order"

    fun queue(): String =
        if (pending.isEmpty()) "QUEUE -" else "QUEUE ${pending.joinToString(",")}"

    fun run(pageSize: Int, maxPages: Int): BatchResult {
        val receipts = mutableListOf<PageReceipt>()
        while (pending.isNotEmpty() && receipts.size < maxPages) {
            receipts.add(processPage(receipts.size + 1, pageSize))
        }
        return BatchResult(receipts, pending.size)
    }

    private fun processPage(number: Int, pageSize: Int): PageReceipt {
        val page = pending.subList(0, minOf(pageSize, pending.size))
        val outcomes = attemptOrders(page)
        val retries = retryOrders(page, outcomes)
        if (retries.isNotEmpty()) pending.addAll(retries)
        val receipt = PageReceipt.capture(number, page, outcomes)
        pending.subList(0, page.size).clear()
        return receipt
    }

    private fun attemptOrders(orderIds: List<String>): List<AttemptOutcome> =
        orderIds.map { orderId -> orders.getValue(orderId).attempt() }

    private fun retryOrders(
        orderIds: List<String>,
        outcomes: List<AttemptOutcome>,
    ): List<String> = orderIds.filterIndexed { index, _ ->
        outcomes[index] == AttemptOutcome.RETRY
    }
}
