const val SCORE_LIMIT = 1_000_000_000_000L

enum class UpdateKind { ADD, SET }

sealed interface Command {
    data class Update(val user: Int, val value: Long, val kind: UpdateKind) : Command
    data class Rank(val user: Int) : Command
    data object Invalid : Command
}

private val integerPattern = Regex("0|-?[1-9][0-9]*")
private val separators = Regex("[ \\t]+")

fun integer(token: String, minimum: Long, maximum: Long): Long? {
    if (!integerPattern.matches(token)) return null
    return token.toLongOrNull()?.takeIf { it in minimum..maximum }
}

fun userId(token: String): Int? = integer(token, 1, Int.MAX_VALUE.toLong())?.toInt()

fun parseCommand(line: String): Command {
    val tokens = line.split(separators)
    return when (tokens.first()) {
        "ADD", "SET" -> {
            if (tokens.size != 3) return Command.Invalid
            val user = userId(tokens[1]) ?: return Command.Invalid
            val value = integer(tokens[2], -SCORE_LIMIT, SCORE_LIMIT) ?: return Command.Invalid
            Command.Update(user, value, UpdateKind.valueOf(tokens[0]))
        }
        "RANK" -> {
            if (tokens.size != 2) return Command.Invalid
            Command.Rank(userId(tokens[1]) ?: return Command.Invalid)
        }
        else -> Command.Invalid
    }
}
