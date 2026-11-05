class EventCommands(
    private val manager: EventManager,
    private val time: EventTime,
) {
    private val eventId = Regex("[a-z][a-z0-9-]{0,23}")
    private val decimal = Regex("[0-9]+")

    suspend fun execute(line: String): String {
        val tokens = line.trim().split(Regex("\\s+"))
        return when (tokens.first()) {
            "START" -> start(tokens)
            "STATUS" -> withId(tokens) { manager.status(it) }
            "STOP" -> withId(tokens) { manager.stop(it) }
            "LIST" -> if (tokens.size == 1) manager.list() else "ERROR INVALID"
            "ADVANCE" -> advance(tokens)
            else -> "ERROR INVALID"
        }
    }

    private fun start(tokens: List<String>): String {
        if (tokens.size != 4 || !eventId.matches(tokens[1])) return "ERROR INVALID"
        val duration = milliseconds(tokens[2], 1L..1_000_000L)
            ?: return "ERROR INVALID"
        val interval = milliseconds(tokens[3], 1L..1_000_000L)
            ?: return "ERROR INVALID"
        return manager.start(tokens[1], duration, interval)
    }

    private suspend fun withId(
        tokens: List<String>,
        action: suspend (String) -> String,
    ): String {
        if (tokens.size != 2 || !eventId.matches(tokens[1])) return "ERROR INVALID"
        return action(tokens[1])
    }

    private fun advance(tokens: List<String>): String {
        if (tokens.size != 2) return "ERROR INVALID"
        val amount = milliseconds(tokens[1], 0L..1_000_000_000L)
            ?: return "ERROR INVALID"
        time.advanceBy(amount)
        return "TIME ${time.now()}"
    }

    private fun milliseconds(token: String, range: LongRange): Long? {
        if (!decimal.matches(token)) return null
        return token.toLongOrNull()?.takeIf { it in range }
    }
}
