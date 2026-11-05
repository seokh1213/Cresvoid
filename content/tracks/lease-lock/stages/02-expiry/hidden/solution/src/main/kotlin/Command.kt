sealed interface Command {
    data class Acquire(val key: String, val client: String, val ttl: Long) : Command
    data class Release(val key: String, val acquisitionId: Long) : Command
    data class Status(val key: String) : Command
    data class Advance(val time: Long) : Command
    data class Renew(val key: String, val acquisitionId: Long, val ttl: Long) : Command
    data object Ignore : Command
    data object Invalid : Command
}

class CommandParser {
    private val separator = Regex("[ \\t]+")
    private val name = Regex("[A-Za-z][A-Za-z0-9_-]{0,31}")
    private val integer = Regex("-?[0-9]+")

    fun parse(line: String): Command {
        val trimmed = line.trim(' ', '\t')
        if (trimmed.isEmpty()) return Command.Ignore
        val tokens = trimmed.split(separator)
        return when (tokens.first()) {
            "ACQUIRE" -> parseAcquire(tokens)
            "RELEASE" -> parseRelease(tokens)
            "STATUS" -> parseStatus(tokens)
            "ADVANCE" -> parseAdvance(tokens)
            "RENEW" -> parseRenew(tokens)
            else -> Command.Invalid
        }
    }

    private fun parseAcquire(tokens: List<String>): Command {
        if (tokens.size != 4 || !name.matches(tokens[1]) || !name.matches(tokens[2])) {
            return Command.Invalid
        }
        val ttl = parseInteger(tokens[3], 1L..1_000_000_000_000L) ?: return Command.Invalid
        return Command.Acquire(tokens[1], tokens[2], ttl)
    }

    private fun parseRelease(tokens: List<String>): Command {
        if (tokens.size != 3 || !name.matches(tokens[1])) return Command.Invalid
        val acquisitionId = parseInteger(tokens[2], 1L..Long.MAX_VALUE) ?: return Command.Invalid
        return Command.Release(tokens[1], acquisitionId)
    }

    private fun parseStatus(tokens: List<String>): Command {
        if (tokens.size != 2 || !name.matches(tokens[1])) return Command.Invalid
        return Command.Status(tokens[1])
    }

    private fun parseInteger(token: String, range: LongRange): Long? {
        if (!integer.matches(token)) return null
        val value = token.toLongOrNull() ?: return null
        return value.takeIf { it in range }
    }

    private fun parseAdvance(tokens: List<String>): Command {
        if (tokens.size != 2) return Command.Invalid
        val time = parseInteger(tokens[1], 0L..1_000_000_000_000L) ?: return Command.Invalid
        return Command.Advance(time)
    }

    private fun parseRenew(tokens: List<String>): Command {
        if (tokens.size != 4 || !name.matches(tokens[1])) return Command.Invalid
        val acquisitionId = parseInteger(tokens[2], 1L..Long.MAX_VALUE) ?: return Command.Invalid
        val ttl = parseInteger(tokens[3], 1L..1_000_000_000_000L) ?: return Command.Invalid
        return Command.Renew(tokens[1], acquisitionId, ttl)
    }
}
