sealed interface Command {
    data class Follow(val reader: Int, val author: Int) : Command
    data class Publish(val id: Int, val author: Int, val timestamp: Long) : Command
    data class Feed(val reader: Int) : Command
}

fun parseCommand(line: String): Command {
    val tokens = line.split(' ')
    return when (tokens[0]) {
        "FOLLOW" -> Command.Follow(tokens[1].toInt(), tokens[2].toInt())
        "POST" -> Command.Publish(tokens[1].toInt(), tokens[2].toInt(), tokens[3].toLong())
        "FEED" -> Command.Feed(tokens[1].toInt())
        else -> error("Unsupported command")
    }
}

data class Post(val id: Int, val author: Int, val timestamp: Long)

val newestFirst: Comparator<Post> = compareByDescending<Post> { it.timestamp }.thenByDescending { it.id }
