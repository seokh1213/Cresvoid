private val INTEGER_FORMAT = Regex("0|[1-9][0-9]*")
private val NAME_FORMAT = Regex("[A-Za-z0-9._:-]{1,64}")
private val CAPACITY_RANGE = 1L..1_000_000_000L
private val REFILL_RATE_RANGE = 1L..1_000_000L
private val QUEUE_LENGTH_RANGE = 1L..1_000_000L
private val LEAK_INTERVAL_RANGE = 1L..1_000_000L
private val LIMIT_RANGE = 1L..1_000_000_000L
private val WINDOW_LENGTH_RANGE = 1L..1_000_000_000L
private val TIME_RANGE = 0L..1_000_000_000_000L

fun parseCommand(line: String): Command {
    val words = line.split(' ').filter(String::isNotEmpty)
    val arguments = Arguments(words.drop(1))
    return when (words.first()) {
        "TOKEN" -> arguments.read(3) {
            Command.DefineTokenBucket(
                name(0),
                capacity = number(1, CAPACITY_RANGE),
                refillPerSecond = number(2, REFILL_RATE_RANGE),
            )
        }
        "LEAKY" -> arguments.read(3) {
            Command.DefineLeakyBucket(
                name(0),
                queueLength = number(1, QUEUE_LENGTH_RANGE),
                leakInterval = number(2, LEAK_INTERVAL_RANGE),
            )
        }
        "WINDOW" -> arguments.read(3) {
            Command.DefineWindow(
                name(0),
                limit = number(1, LIMIT_RANGE),
                windowLength = number(2, WINDOW_LENGTH_RANGE),
            )
        }
        "REQ" -> arguments.read(3) { Command.Request(limiter = name(0), key = name(1), time = number(2, TIME_RANGE)) }
        "REPORT" -> arguments.read(1) { Command.Report(limiter = name(0)) }
        else -> reject(ErrorCode.UNKNOWN_COMMAND)
    }
}

private class Arguments(private val words: List<String>) {
    fun <T> read(count: Int, build: Arguments.() -> T): T {
        if (words.size != count) reject(ErrorCode.BAD_ARGUMENT)
        return build()
    }

    fun name(index: Int): String = words[index].takeIf(NAME_FORMAT::matches) ?: reject(ErrorCode.BAD_ARGUMENT)

    fun number(index: Int, range: LongRange): Long {
        val word = words[index]
        if (!INTEGER_FORMAT.matches(word)) reject(ErrorCode.BAD_ARGUMENT)
        return word.toLongOrNull()?.takeIf { it in range } ?: reject(ErrorCode.BAD_ARGUMENT)
    }
}
