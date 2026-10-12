private val UNSIGNED_INTEGER = Regex("0|[1-9][0-9]*")
private val SIGNED_INTEGER = Regex("0|-?[1-9][0-9]*")
private val METRIC_NAME = Regex("[A-Za-z_:][A-Za-z0-9_:]{0,63}")
private val LABEL_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
private val LABEL_VALUE = Regex("[A-Za-z0-9_.:/-]{1,64}")

private val TIME_RANGE = 0L..1_000_000_000L
private val LENGTH_RANGE = 1L..1_000_000_000L
private val COUNTER_VALUE_RANGE = 0L..1_000_000_000_000L
private val GAUGE_VALUE_RANGE = -1_000_000_000_000L..1_000_000_000_000L

private val METRIC_KINDS = mapOf("counter" to MetricKind.COUNTER, "gauge" to MetricKind.GAUGE)

private const val UNBOUNDED = Int.MAX_VALUE

fun parseCommand(line: String): Command {
    val words = line.split(' ').filter(String::isNotEmpty)
    val arguments = Arguments(words.drop(1))
    return when (words.first()) {
        "TYPE" -> arguments.read(2..2) { Command.DefineMetric(metricName(0), metricKind(1)) }
        "ADD" -> arguments.read(3..UNBOUNDED) { Command.AddSample(time(0), word(1), metricName(2), labels(from = 3)) }
        "INCREASE" -> arguments.read(3..UNBOUNDED) { Command.Increase(window(0), selector(2)) }
        "RATE" -> arguments.read(3..UNBOUNDED) { Command.Rate(window(0), selector(2)) }
        "DELTA" -> arguments.read(3..UNBOUNDED) { Command.Delta(window(0), selector(2)) }
        else -> reject(ErrorCode.UNKNOWN_COMMAND)
    }
}

fun parseCounterValue(text: String): Long = integer(text, UNSIGNED_INTEGER, COUNTER_VALUE_RANGE)

fun parseGaugeValue(text: String): Long = integer(text, SIGNED_INTEGER, GAUGE_VALUE_RANGE)

private class Arguments(private val words: List<String>) {
    fun <T> read(counts: IntRange, build: Arguments.() -> T): T {
        if (words.size !in counts) reject(ErrorCode.BAD_ARGUMENT)
        return build()
    }

    fun word(index: Int): String = words[index]

    fun time(index: Int): Long = integer(words[index], UNSIGNED_INTEGER, TIME_RANGE)

    fun length(index: Int): Long = integer(words[index], UNSIGNED_INTEGER, LENGTH_RANGE)

    fun window(index: Int): RangeWindow = RangeWindow(end = time(index), length = length(index + 1))

    fun metricName(index: Int): String = matching(words[index], METRIC_NAME)

    fun metricKind(index: Int): MetricKind = METRIC_KINDS[words[index]] ?: reject(ErrorCode.BAD_ARGUMENT)

    fun selector(index: Int): Selector = Selector(metricName(index), labels(from = index + 1))

    fun labels(from: Int): Set<LabelPair> {
        val pairs = words.drop(from).map(::labelPair)
        if (pairs.distinctBy(LabelPair::name).size != pairs.size) reject(ErrorCode.BAD_ARGUMENT)
        return pairs.toSet()
    }
}

private fun labelPair(word: String): LabelPair {
    val separator = word.indexOf('=')
    if (separator < 0) reject(ErrorCode.BAD_ARGUMENT)
    return LabelPair(
        name = matching(word.substring(0, separator), LABEL_NAME),
        value = matching(word.substring(separator + 1), LABEL_VALUE),
    )
}

private fun matching(word: String, format: Regex): String =
    word.takeIf(format::matches) ?: reject(ErrorCode.BAD_ARGUMENT)

private fun integer(word: String, format: Regex, range: LongRange): Long =
    matching(word, format).toLongOrNull()?.takeIf { it in range } ?: reject(ErrorCode.BAD_ARGUMENT)
