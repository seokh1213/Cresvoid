class OrderReport(private val snapshot: InboxSnapshot, private val cutoff: Long) {
    private val audit = mutableListOf<String>()
    private var builds = 0
    private val projection: Projection by lazy {
        builds++
        audit += "project"
        val selection = snapshot.select(cutoff)
        val orders = selection.events.groupBy { it.orderId }
            .toSortedMap()
            .map { (orderId, events) -> replayOrder(orderId, events) }
        Projection(orders, selection)
    }

    fun orderLine(orderId: String): String {
        val replay = projection.order(orderId)
        val first = projection.selection.first?.toString() ?: "-"
        return "${replay.summary()}|first=$first|${diagnostics()}"
    }

    fun planLine(size: Int): String {
        val shipments = projection.orders.filter { it.state is OrderState.Shipped }
            .map { it.orderId }
        val checks = shipments.windowed(size).formatGroups()
        val batches = shipments.chunked(size).formatGroups()
        return "checks=$checks|batches=$batches|${diagnostics()}"
    }

    fun exportLine(): String {
        audit += "open"
        val exported = runCatching {
            try {
                val totals = projection.totals()
                projection.orders.forEach { order ->
                    check(order.pending == 0) { "gap:${order.orderId}:${order.nextSequence}" }
                }
                val records = projection.orders.joinToString(";") { it.exportRecord().toString() }
                    .ifEmpty { "-" }
                audit += "write"
                "OK|$totals|records=$records"
            } finally {
                audit += "close"
            }
        }.onFailure {
            audit += "failed"
        }
        val status = exported.getOrElse { "ERROR|${it.message}" }
        return "$status|audit=${audit.joinToString(",")}|${diagnostics()}"
    }

    fun traceLine(): String {
        val trace = projection.selection.readTrace.joinToString(",").ifEmpty { "-" }
        return "trace=$trace|${projection.totals()}|${diagnostics()}"
    }

    private fun diagnostics(): String =
        "reads=${projection.selection.readTrace.size}|builds=$builds"

    private fun List<List<String>>.formatGroups(): String =
        joinToString("/") { it.joinToString("+") }.ifEmpty { "-" }
}
