sealed interface Mode {
    data object Write : Mode
    data object Read : Mode
    data class Hybrid(val threshold: Int) : Mode
}

fun parseMode(tokens: List<String>): Mode = when (tokens[1]) {
    "WRITE" -> Mode.Write
    "READ" -> Mode.Read
    "HYBRID" -> Mode.Hybrid(tokens[3].toInt())
    else -> error("Unsupported mode")
}

sealed interface Command {
    data class Follow(val reader: Int, val author: Int) : Command
    data class Unfollow(val reader: Int, val author: Int) : Command
    data class Publish(val id: Int, val author: Int, val timestamp: Long) : Command
    data class Delete(val id: Int) : Command
    data class Feed(val reader: Int) : Command
}

fun parseCommand(line: String): Command {
    val tokens = line.split(' ')
    return when (tokens[0]) {
        "FOLLOW" -> Command.Follow(tokens[1].toInt(), tokens[2].toInt())
        "UNFOLLOW" -> Command.Unfollow(tokens[1].toInt(), tokens[2].toInt())
        "POST" -> Command.Publish(tokens[1].toInt(), tokens[2].toInt(), tokens[3].toLong())
        "DELETE" -> Command.Delete(tokens[1].toInt())
        "FEED" -> Command.Feed(tokens[1].toInt())
        else -> error("Unsupported command")
    }
}

data class Post(val id: Int, val author: Int, val timestamp: Long)

val newestFirst: Comparator<Post> = compareByDescending<Post> { it.timestamp }.thenByDescending { it.id }
