private val INTEGER_FORMAT = Regex("0|[1-9][0-9]*")
private val NODE_NAME_FORMAT = Regex("[a-z][a-z0-9]{0,7}")
private val ELECTION_TIMEOUT_RANGE = 1L..1_000_000L
private val TICK_RANGE = 1L..1_000_000L
private val DELAY_RANGE = 1L..1_000_000L
private val DROP_RANGE = 0L..1_000_000L

fun parseCommand(line: String): Command {
    val words = line.split(' ').filter(String::isNotEmpty)
    val arguments = Arguments(words.drop(1))
    return when (words.first()) {
        "NODE" -> arguments.read(2..11) {
            val electionTimeouts = (1 until count).map { number(it, ELECTION_TIMEOUT_RANGE) }
            Command.CreateNode(nodeName(0), electionTimeouts)
        }
        "TICK" -> arguments.read(1..1) { Command.Tick(number(0, TICK_RANGE)) }
        "DELAY" -> arguments.read(3..3) {
            Command.SetDelay(sender = nodeName(0), receiver = nodeName(1), ticks = number(2, DELAY_RANGE))
        }
        "DROP" -> arguments.read(3..3) {
            Command.SetDrop(sender = nodeName(0), receiver = nodeName(1), count = number(2, DROP_RANGE))
        }
        "PARTITION" -> arguments.read(1..Int.MAX_VALUE) { Command.Partition(groups = (0 until count).map(::group)) }
        "HEAL" -> arguments.read(0..0) { Command.Heal }
        "STATE" -> arguments.read(1..1) { Command.ShowState(nodeName(0)) }
        else -> reject(ErrorCode.UNKNOWN_COMMAND)
    }
}

private class Arguments(private val words: List<String>) {
    val count: Int get() = words.size

    fun <T> read(counts: IntRange, build: Arguments.() -> T): T {
        if (words.size !in counts) reject(ErrorCode.BAD_ARGUMENT)
        return build()
    }

    fun nodeName(index: Int): String = matching(index, NODE_NAME_FORMAT)

    fun matching(index: Int, format: Regex): String =
        words[index].takeIf(format::matches) ?: reject(ErrorCode.BAD_ARGUMENT)

    fun number(index: Int, range: LongRange): Long {
        val word = matching(index, INTEGER_FORMAT)
        return word.toLongOrNull()?.takeIf { it in range } ?: reject(ErrorCode.BAD_ARGUMENT)
    }

    fun group(index: Int): List<String> {
        val members = words[index].split(',')
        if (!members.all(NODE_NAME_FORMAT::matches)) reject(ErrorCode.BAD_ARGUMENT)
        return members
    }
}
