private class OrderCommands {
    private val inbox = EventInbox()

    fun execute(line: String): String? {
        val fields = line.trim().split(Regex("\\s+"))
        return when (fields[0]) {
            "EVENT" -> {
                fields.expectSize(5)
                val orderId = fields[1].orderId()
                val sequence = fields[2].toInt().also { require(it > 0) }
                val kind = EventKind.valueOf(fields[3])
                val occurredAt = fields[4].timestamp()
                inbox.append(OrderEvent(EventKey(orderId, sequence), kind, occurredAt))
                null
            }
            "REPORT" -> {
                fields.expectSize(3)
                report(fields[1]).orderLine(fields[2].orderId())
            }
            "RECEIPTS" -> {
                fields.expectSize(2)
                inbox.snapshot().receipts(fields[1].orderId())
            }
            "PLAN" -> {
                fields.expectSize(3)
                val size = fields[2].toInt().also { require(it > 0) }
                report(fields[1]).planLine(size)
            }
            "EXPORT" -> {
                fields.expectSize(2)
                report(fields[1]).exportLine()
            }
            "TRACE" -> {
                fields.expectSize(2)
                report(fields[1]).traceLine()
            }
            else -> throw IllegalArgumentException("command")
        }
    }

    private fun report(cutoff: String): OrderReport =
        OrderReport(inbox.snapshot(), cutoff.timestamp())

    private fun List<String>.expectSize(size: Int) {
        require(this.size == size)
    }

    private fun String.orderId(): String {
        require(matches(Regex("[A-Za-z][A-Za-z0-9_-]*")))
        return this
    }

    private fun String.timestamp(): Long = toLong().also { require(it >= 0) }
}

fun main() {
    val commands = OrderCommands()
    System.`in`.bufferedReader().lineSequence().forEach { line ->
        if (line.isBlank()) return@forEach
        val output = try {
            commands.execute(line)
        } catch (_: IllegalArgumentException) {
            "ERROR input"
        }
        if (output != null) println(output)
    }
}
