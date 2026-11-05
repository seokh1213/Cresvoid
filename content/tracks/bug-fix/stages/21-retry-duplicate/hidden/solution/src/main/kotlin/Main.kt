fun main() {
    val store = ScriptedHistoryStore()
    val publisher = HistoryPublisher(store, HistoryKeys())
    val console = OrderConsole(Orders(publisher), store)
    System.`in`.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            if (line.isNotBlank()) println(console.execute(line))
        }
    }
}

class OrderConsole(
    private val orders: Orders,
    private val store: ScriptedHistoryStore,
) {
    fun execute(line: String): String {
        val tokens = line.trim().split(Regex("\\s+"))
        return try {
            dispatch(tokens)
        } catch (_: InvalidCommand) {
            "ERROR input"
        }
    }

    private fun dispatch(tokens: List<String>): String = when (tokens.first()) {
        "OPEN" -> {
            expectSize(tokens, 3)
            orders.open(orderId(tokens[1]), status(tokens[2]))
        }
        "CHANGE" -> {
            expectSize(tokens, 3)
            orders.change(orderId(tokens[1]), status(tokens[2]))
        }
        "SHOW" -> {
            expectSize(tokens, 2)
            orders.show(orderId(tokens[1]))
        }
        "HISTORY" -> {
            expectSize(tokens, 2)
            store.history(orderId(tokens[1]))
        }
        "PLAN" -> {
            if (tokens.size < 2) throw InvalidCommand()
            val outcomes = tokens.drop(1).map { token ->
                CallOutcome.entries.find { it.name == token } ?: throw InvalidCommand()
            }
            store.schedule(outcomes)
        }
        "METRICS" -> {
            expectSize(tokens, 1)
            store.metrics()
        }
        else -> throw InvalidCommand()
    }

    private fun expectSize(tokens: List<String>, expected: Int) {
        if (tokens.size != expected) throw InvalidCommand()
    }

    private fun orderId(token: String): String {
        if (!ORDER_ID.matches(token)) throw InvalidCommand()
        return token
    }

    private fun status(token: String): OrderStatus =
        OrderStatus.entries.find { it.name == token } ?: throw InvalidCommand()

    private companion object {
        val ORDER_ID = Regex("[A-Za-z0-9-]{1,32}")
    }
}

private class InvalidCommand : RuntimeException()
