import java.math.BigDecimal

private val UNSIGNED_INTEGER = Regex("0|[1-9][0-9]*")
private val SIGNED_INTEGER = Regex("0|-?[1-9][0-9]*")
private val METRIC_NAME = Regex("[A-Za-z_:][A-Za-z0-9_:]{0,63}")
private val LABEL_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
private val LABEL_VALUE = Regex("[A-Za-z0-9_.:/-]{1,64}")
private val QUANTILE_LEVEL = Regex("0\\.[0-9]{1,3}|1")
private val RULE_NAME = Regex("[A-Za-z0-9._:-]{1,64}")
private val OBJECTIVE = Regex("(0|[1-9][0-9]?)(\\.[0-9]{1,3})?")
private val THRESHOLD = Regex("(0|[1-9][0-9]{0,5})(\\.[0-9]{1,3})?")

private val TIME_RANGE = 0L..1_000_000_000L
private val LENGTH_RANGE = 1L..1_000_000_000L
private val UPPER_BOUND_RANGE = 1L..1_000_000_000L
private val COUNTER_VALUE_RANGE = 0L..1_000_000_000_000L
private val GAUGE_VALUE_RANGE = -1_000_000_000_000L..1_000_000_000_000L
private val MAX_OBJECTIVE = BigDecimal(100)
private val MAX_THRESHOLD = BigDecimal(100_000)

private const val HISTOGRAM = "histogram"
private val METRIC_KINDS = mapOf("counter" to MetricKind.Counter, "gauge" to MetricKind.Gauge)

private const val UNBOUNDED = Int.MAX_VALUE

fun parseCommand(line: String): Command {
    val words = line.split(' ').filter(String::isNotEmpty)
    val arguments = Arguments(words.drop(1))
    return when (words.first()) {
        "TYPE" -> parseDefineMetric(arguments)
        "ADD" -> arguments.read(3..UNBOUNDED) { Command.AddSample(time(0), word(1), metricName(2), labels(from = 3)) }
        "INCREASE" -> arguments.read(3..UNBOUNDED) { Command.Increase(window(0), selector(2)) }
        "RATE" -> arguments.read(3..UNBOUNDED) { Command.Rate(window(0), selector(2)) }
        "DELTA" -> arguments.read(3..UNBOUNDED) { Command.Delta(window(0), selector(2)) }
        "BUCKETS" -> arguments.read(3..UNBOUNDED) { Command.Buckets(window(0), selector(2)) }
        "QUANTILE" -> arguments.read(4..UNBOUNDED) { Command.Quantile(window(0), quantileLevel(2), selector(3)) }
        "SLO" -> arguments.read(4..UNBOUNDED) { parseDefineSlo() }
        "BURN" -> arguments.read(3..3) { Command.Burn(window(0), ruleName(2)) }
        "ALERT" -> arguments.read(5..5) { parseDefineAlert() }
        "EVAL" -> arguments.read(3..3) { parseEvaluate() }
        else -> reject(ErrorCode.UNKNOWN_COMMAND)
    }
}

fun parseCounterValue(text: String): Long = integer(text, UNSIGNED_INTEGER, COUNTER_VALUE_RANGE)

fun parseGaugeValue(text: String): Long = integer(text, SIGNED_INTEGER, GAUGE_VALUE_RANGE)

fun parseBucketCounts(text: String, bucketCount: Int): List<Long> {
    val counts = text.split(',').map { piece -> integer(piece, UNSIGNED_INTEGER, COUNTER_VALUE_RANGE) }
    if (counts.size != bucketCount) reject(ErrorCode.BAD_ARGUMENT)
    if (counts.zipWithNext().any { (lower, upper) -> upper < lower }) reject(ErrorCode.BAD_ARGUMENT)
    return counts
}

private fun parseDefineMetric(arguments: Arguments): Command.DefineMetric =
    if (arguments.wordOrNull(1) == HISTOGRAM) {
        arguments.read(3..UNBOUNDED) {
            Command.DefineMetric(metricName(0), MetricKind.Histogram(upperBounds(from = 2)))
        }
    } else {
        arguments.read(2..2) { Command.DefineMetric(metricName(0), metricKind(1)) }
    }

private fun Arguments.parseDefineSlo(): Command.DefineSlo {
    val name = ruleName(0)
    val objective = decimal(1, OBJECTIVE) { it < MAX_OBJECTIVE }
    val totalMetric = metricName(2)
    val badMetric = metricName(3)
    val labels = labels(from = 4)
    return Command.DefineSlo(name, objective, total = Selector(totalMetric, labels), bad = Selector(badMetric, labels))
}

private fun Arguments.parseDefineAlert(): Command.DefineAlert {
    val alert = Command.DefineAlert(
        name = ruleName(0),
        slo = ruleName(1),
        longWindow = length(2),
        shortWindow = length(3),
        threshold = decimal(4, THRESHOLD) { it <= MAX_THRESHOLD },
    )
    if (alert.shortWindow >= alert.longWindow) reject(ErrorCode.BAD_ARGUMENT)
    return alert
}

private fun Arguments.parseEvaluate(): Command.Evaluate {
    val evaluate = Command.Evaluate(start = time(0), end = time(1), interval = length(2))
    if (evaluate.start > evaluate.end) reject(ErrorCode.BAD_ARGUMENT)
    return evaluate
}

private class Arguments(private val words: List<String>) {
    fun <T> read(counts: IntRange, build: Arguments.() -> T): T {
        if (words.size !in counts) reject(ErrorCode.BAD_ARGUMENT)
        return build()
    }

    fun word(index: Int): String = words[index]

    fun wordOrNull(index: Int): String? = words.getOrNull(index)

    fun time(index: Int): Long = integer(words[index], UNSIGNED_INTEGER, TIME_RANGE)

    fun length(index: Int): Long = integer(words[index], UNSIGNED_INTEGER, LENGTH_RANGE)

    fun window(index: Int): RangeWindow = RangeWindow(end = time(index), length = length(index + 1))

    fun metricName(index: Int): String = matching(words[index], METRIC_NAME)

    fun ruleName(index: Int): String = matching(words[index], RULE_NAME)

    fun metricKind(index: Int): MetricKind = METRIC_KINDS[words[index]] ?: reject(ErrorCode.BAD_ARGUMENT)

    fun upperBounds(from: Int): List<Long> {
        val bounds = words.drop(from).map { word -> integer(word, UNSIGNED_INTEGER, UPPER_BOUND_RANGE) }
        if (bounds.zipWithNext().any { (lower, upper) -> upper <= lower }) reject(ErrorCode.BAD_ARGUMENT)
        return bounds
    }

    fun quantileLevel(index: Int): BigDecimal = decimal(index, QUANTILE_LEVEL) { true }

    fun decimal(index: Int, format: Regex, withinLimit: (BigDecimal) -> Boolean): BigDecimal {
        val value = BigDecimal(matching(words[index], format))
        return value.takeIf { it.signum() > 0 && withinLimit(it) } ?: reject(ErrorCode.BAD_ARGUMENT)
    }

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
