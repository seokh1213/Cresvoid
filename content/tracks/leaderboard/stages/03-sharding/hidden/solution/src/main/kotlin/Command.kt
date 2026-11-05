const val SCORE_LIMIT = 1_000_000_000_000L

enum class UpdateKind { ADD, SET }
enum class ShardMode { RANGE, HASH }

sealed interface Command {
    data class Update(val user: Int, val value: Long, val kind: UpdateKind, val board: String) : Command
    data class Rank(val user: Int, val board: String) : Command
    data class Top(val count: Int, val board: String) : Command
    data class Around(val user: Int, val radius: Int, val board: String) : Command
    data class Create(val board: String, val period: Long) : Command
    data class Tick(val time: Long) : Command
    data class Reset(val board: String) : Command
    data class Distribute(val board: String, val boundaries: List<Long>) : Command
    data class DistributedRank(val board: String, val mode: ShardMode, val user: Int) : Command
    data class Counts(val board: String, val mode: ShardMode) : Command
    data object Invalid : Command
}

private val integerPattern = Regex("0|-?[1-9][0-9]*")
private val boardPattern = Regex("[a-z][a-z0-9_]{0,15}")
private val separators = Regex("[ \\t]+")

fun integer(token: String, minimum: Long, maximum: Long): Long? {
    if (!integerPattern.matches(token)) return null
    return token.toLongOrNull()?.takeIf { it in minimum..maximum }
}

fun userId(token: String): Int? = integer(token, 1, Int.MAX_VALUE.toLong())?.toInt()
fun boardName(token: String): String? = token.takeIf(boardPattern::matches)

fun parseCommand(line: String): Command {
    val tokens = line.split(separators)
    return when (tokens.first()) {
        "ADD", "SET", "RANK", "TOP", "AROUND" -> parseBoardCommand(tokens, "all")
        "BADD", "BSET", "BRANK", "BTOP", "BAROUND" -> {
            if (tokens.size < 2) return Command.Invalid
            val board = boardName(tokens[1]) ?: return Command.Invalid
            parseBoardCommand(listOf(tokens[0].drop(1)) + tokens.drop(2), board)
        }
        "CREATE" -> {
            if (tokens.size != 3) return Command.Invalid
            val board = boardName(tokens[1]) ?: return Command.Invalid
            val period = integer(tokens[2], 0, SCORE_LIMIT) ?: return Command.Invalid
            Command.Create(board, period)
        }
        "TICK" -> {
            if (tokens.size != 2) return Command.Invalid
            Command.Tick(integer(tokens[1], 0, SCORE_LIMIT) ?: return Command.Invalid)
        }
        "RESET" -> {
            if (tokens.size != 2) return Command.Invalid
            Command.Reset(boardName(tokens[1]) ?: return Command.Invalid)
        }
        "DISTRIBUTE" -> parseDistribution(tokens)
        "DRANK", "DCOUNTS" -> parseShardQuery(tokens)
        else -> Command.Invalid
    }
}

private fun parseDistribution(tokens: List<String>): Command {
    if (tokens.size < 3) return Command.Invalid
    val board = boardName(tokens[1]) ?: return Command.Invalid
    val count = integer(tokens[2], 1, 32)?.toInt() ?: return Command.Invalid
    if (tokens.size != count + 2) return Command.Invalid
    val boundaries = mutableListOf<Long>()
    for (token in tokens.drop(3)) {
        val boundary = integer(token, -SCORE_LIMIT, SCORE_LIMIT) ?: return Command.Invalid
        if (boundaries.isNotEmpty() && boundary <= boundaries.last()) return Command.Invalid
        boundaries.add(boundary)
    }
    return Command.Distribute(board, boundaries)
}

private fun parseShardQuery(tokens: List<String>): Command {
    val expectedSize = if (tokens[0] == "DRANK") 4 else 3
    if (tokens.size != expectedSize) return Command.Invalid
    val board = boardName(tokens[1]) ?: return Command.Invalid
    val mode = when (tokens[2]) {
        "RANGE" -> ShardMode.RANGE
        "HASH" -> ShardMode.HASH
        else -> return Command.Invalid
    }
    return if (tokens[0] == "DCOUNTS") Command.Counts(board, mode)
    else Command.DistributedRank(board, mode, userId(tokens[3]) ?: return Command.Invalid)
}

private fun parseBoardCommand(tokens: List<String>, board: String): Command = when (tokens[0]) {
    "ADD", "SET" -> parseUpdate(tokens, board)
    "RANK" -> {
        if (tokens.size != 2) Command.Invalid
        else userId(tokens[1])?.let { Command.Rank(it, board) } ?: Command.Invalid
    }
    "TOP" -> {
        if (tokens.size != 2) Command.Invalid
        else integer(tokens[1], 0, 1000)?.let { Command.Top(it.toInt(), board) } ?: Command.Invalid
    }
    "AROUND" -> parseAround(tokens, board)
    else -> Command.Invalid
}

private fun parseUpdate(tokens: List<String>, board: String): Command {
    if (tokens.size != 3) return Command.Invalid
    val user = userId(tokens[1]) ?: return Command.Invalid
    val value = integer(tokens[2], -SCORE_LIMIT, SCORE_LIMIT) ?: return Command.Invalid
    return Command.Update(user, value, UpdateKind.valueOf(tokens[0]), board)
}

private fun parseAround(tokens: List<String>, board: String): Command {
    if (tokens.size != 3) return Command.Invalid
    val user = userId(tokens[1]) ?: return Command.Invalid
    val radius = integer(tokens[2], 0, 500)?.toInt() ?: return Command.Invalid
    return Command.Around(user, radius, board)
}
