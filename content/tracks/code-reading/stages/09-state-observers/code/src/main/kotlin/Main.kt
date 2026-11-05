class OrderConsole {
    private val journal = NotificationJournal()
    private val registrations = linkedMapOf<String, OrderRegistration>()

    fun execute(line: String) {
        val fields = line.trim().split(Regex("\\s+"))
        when (fields[0]) {
            "CREATE" -> create(fields)
            "SUB" -> subscribe(fields)
            "UNSUB" -> unsubscribe(fields)
            "DO" -> request(fields)
            "STATUS" -> showStatus(fields)
            "TRACE" -> showTrace(fields)
            else -> error("Unknown command: ${fields[0]}")
        }
    }

    private fun create(fields: List<String>) {
        require(fields.size == 3) { "CREATE id IGNORE|REJECT" }
        val id = fields[1]
        require(id !in registrations) { "Duplicate order: $id" }
        val policy = RejectionPolicy.valueOf(fields[2])
        val order = Order(id, policy)
        registrations[id] = OrderRegistration(order, journal)
    }

    private fun subscribe(fields: List<String>) {
        require(fields.size == 3) { "SUB id observer" }
        val registration = registration(fields[1])
        registration.order.subscribe(registration.observer(fields[2]))
    }

    private fun unsubscribe(fields: List<String>) {
        require(fields.size == 3) { "UNSUB id observer" }
        val registration = registration(fields[1])
        registration.order.unsubscribe(registration.observer(fields[2]))
    }

    private fun request(fields: List<String>) {
        require(fields.size == 4) { "DO id PAY|SHIP|CANCEL reference" }
        val order = registration(fields[1]).order
        val request = OrderRequest(OrderAction.valueOf(fields[2]), fields[3])
        try {
            if (!order.request(request)) {
                journal.recordRejected(order, request.action, "ignored")
            }
        } catch (rejection: RejectedTransition) {
            journal.recordRejected(order, rejection.action, "rejected")
        }
    }

    private fun showStatus(fields: List<String>) {
        require(fields.size == 2) { "STATUS id" }
        println(registration(fields[1]).order.summary())
    }

    private fun showTrace(fields: List<String>) {
        require(fields.size == 1) { "TRACE" }
        println(journal.render())
    }

    private fun registration(id: String): OrderRegistration =
        requireNotNull(registrations[id]) { "Unknown order: $id" }
}

fun main() {
    val console = OrderConsole()
    while (true) {
        val line = readlnOrNull() ?: break
        if (line.isNotBlank()) {
            console.execute(line)
        }
    }
}
