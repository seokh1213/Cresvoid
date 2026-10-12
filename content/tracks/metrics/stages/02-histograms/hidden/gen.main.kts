import kotlin.random.Random

class Options(
    val mode: String,
    val lines: Int,
    val series: Int,
    val samples: Int,
    val queries: Int,
    val seed: Long,
)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--mode" to "normal",
        "--lines" to "400",
        "--series" to "20000",
        "--samples" to "300000",
        "--queries" to "40000",
        "--seed" to "1",
    )
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    val options = Options(
        mode = values.getValue("--mode"),
        lines = values.getValue("--lines").toInt(),
        series = values.getValue("--series").toInt(),
        samples = values.getValue("--samples").toInt(),
        queries = values.getValue("--queries").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.mode == "normal" || options.mode == "large") { "--mode 는 normal 이나 large 다" }
    require(options.lines in 10..100_000) { "--lines 는 10~100,000 이다" }
    require(options.series in 1_000..40_000) { "--series 는 1,000~40,000 이다" }
    require(options.samples in options.series * 4..500_000) { "--samples 는 시계열 수의 네 배 이상 500,000 이하다" }
    require(options.queries in 1_000..100_000) { "--queries 는 1,000~100,000 이다" }
    return options
}

val options = parseOptions(args)
val random = Random(options.seed)
val output = System.out.bufferedWriter()

fun emit(line: String) {
    output.write(line)
    output.write("\n")
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

fun cumulative(perBucket: List<Long>): List<Long> = perBucket.runningReduce(Long::plus)

val MAX_TIME = 1_000_000_000L
val MAX_COUNTER = 1_000_000_000_000L

enum class Kind(val word: String) { COUNTER("counter"), GAUGE("gauge"), HISTOGRAM("histogram") }

class SeriesModel(val labels: Map<String, String>, val interval: Long, val exact: Boolean, firstTime: Long) {
    val times = ArrayList<Long>()
    val values = ArrayList<Long>()
    val bucketCounts = ArrayList<List<Long>>()
    var nextTime = firstTime
    val lastTime: Long? get() = times.lastOrNull()
}

class MetricModel(val name: String, val kind: Kind, val bounds: List<Long> = emptyList()) {
    val series = ArrayList<SeriesModel>()
    val bucketCount: Int get() = bounds.size + 1

    fun find(labels: Map<String, String>): SeriesModel? = series.firstOrNull { it.labels == labels }
}

val COUNTER_NAMES = listOf("http_requests_total", "errors_total", "jobs_total", "a", ":ratio:sum", "c".repeat(64))
val GAUGE_NAMES = listOf("queue_length", "temperature", "inflight", "g")
val HISTOGRAM_NAMES = listOf(
    "http_request_duration_ms", "rpc_duration_ms", "queue_wait_ms", "payload_bytes", "h", "Latency:bucketed", "d".repeat(64),
)
val MISSING_NAMES = listOf("missing", "nope", "http_request_duration", "Rpc_duration_ms", "queue_wait_ms_bucket")
val BAD_METRIC_NAMES = listOf("9lives", "-a", "a-b", "a.b", "m".repeat(65), "é", "a/b", "1")
val LABEL_NAMES = listOf("job", "instance", "zone", "method", "code", "route", "_", "Job", "x".repeat(64))
val LABEL_VALUES = mapOf(
    "job" to listOf("api", "batch", "web"),
    "instance" to listOf("a", "b", "c", "10.0.0.1:8080", "10.0.0.2:8080"),
    "zone" to listOf("z1", "z2"),
    "method" to listOf("GET", "POST"),
    "code" to listOf("200", "500"),
    "route" to listOf("/v1/users", "/", "-", "a.b:c/d_e"),
    "_" to listOf("_", "0"),
    "Job" to listOf("API"),
    "x".repeat(64) to listOf("v".repeat(64)),
)
val BOUND_SETS = listOf(
    listOf(100L, 200L, 300L, 450L),
    listOf(5L, 10L, 25L, 50L, 100L, 250L, 500L, 1_000L, 2_500L, 5_000L),
    listOf(50L, 100L),
    listOf(1L),
    listOf(1L, 2L, 3L),
    listOf(1_000_000_000L),
    listOf(1L, 1_000_000_000L),
)
val PHI_COMMON = listOf("0.5", "0.9", "0.95", "0.99")
val PHI_RARE = listOf("1", "0.001", "0.999", "0.1", "0.25", "0.75", "0.05", "0.01", "0.995")
val BAD_PHIS = listOf("0", "0.0", "0.000", "1.0", ".5", "0.9999", "1.5", "-0.5", "00.5", "1.000", "0.5x", "5", "0,5", "+0.5", "0.")
val BAD_LABELS = listOf(
    "job", "job=", "=api", "job==api", "job=a=b", "9job=api", "jo-b=api", "job=é", "job=a,b", "job=(x)",
    "j".repeat(65) + "=a", "job=" + "v".repeat(65), "le=+Inf",
)
val BAD_TIMES = listOf("-1", "01", "1000000001", "1.5", "+3", "1e3", "9223372036854775808", "t")
val BAD_LENGTHS = listOf("0", "-5", "01", "1000000001", "1.0", "x", "99999999999999999999")
val BAD_BOUND_LISTS = listOf(
    listOf("50", "100", "100"), listOf("0", "100"), listOf("100", "50"), listOf("010", "20"), listOf("1.5", "3"),
    listOf("-1", "5"), listOf("5", "+Inf"), listOf("1000000001"), listOf("1", "2", "2", "3"), listOf("Inf"),
    listOf("99999999999999999999"),
)
val BAD_COUNTER_VALUES = listOf("-1", "1000000000001", "01", "1.5", "abc", "1,2", "1,2,3")
val BAD_GAUGE_VALUES = listOf("-0", "1000000000001", "+5", "1.0", "1,2")
val UNKNOWN_COMMANDS = listOf(
    "add", "Add", "type", "Increase", "buckets", "Buckets", "BUCKET", "quantile", "Quantile", "QUANTILES",
    "HISTOGRAM", "PERCENTILE", "P99", "SUM", "ERR", "NONE", "-", "BUCKETS\t10", "QUANTILE\t10",
)
val INTERVALS = listOf(10L, 15L, 20L, 30L, 60L, 1L, 7L, 128L)

val metrics = ArrayList<MetricModel>()
var timeBase = 0L
var now = 0L
var emittedLines = 0
var scenarioCount = 0

fun emitLine(line: String) {
    emit(line)
    emittedLines++
}

fun spaced(words: List<String>): String {
    if (chance(0.85)) return words.joinToString(" ")
    val gaps = words.joinToString("") { word -> word + " ".repeat(random.nextInt(1, 4)) }.trimEnd(' ')
    return " ".repeat(random.nextInt(0, 3)) + gaps + " ".repeat(random.nextInt(0, 3))
}

fun labelWords(labels: Map<String, String>): List<String> = labels.entries.map { "${it.key}=${it.value}" }.shuffled(random)

fun randomBounds(): List<Long> {
    if (chance(0.7)) return pick(BOUND_SETS)
    val count = weighted(random.nextInt(1, 4) to 2, random.nextInt(4, 13) to 3)
    return generateSequence { random.nextLong(1, 2_000) }.distinct().take(count).sorted().toList()
}

fun defineMetric(kind: Kind) {
    val pool = when (kind) {
        Kind.COUNTER -> COUNTER_NAMES
        Kind.GAUGE -> GAUGE_NAMES
        Kind.HISTOGRAM -> HISTOGRAM_NAMES
    }
    val unused = pool.filter { name -> metrics.none { it.name == name } }
    if (unused.isEmpty()) return
    val metric = MetricModel(pick(unused), kind, if (kind == Kind.HISTOGRAM) randomBounds() else emptyList())
    emitLine(spaced(listOf("TYPE", metric.name, kind.word) + metric.bounds.map(Long::toString)))
    metrics += metric
    newSeries(metric)
}

fun randomLabels(): Map<String, String> {
    val count = weighted(0 to 1, 1 to 4, 2 to 6, 3 to 2)
    val names = LABEL_NAMES.take(6).shuffled(random).take(count) + listOfNotNull(LABEL_NAMES.drop(6).randomOrNull(random)?.takeIf { chance(0.15) })
    return names.distinct().associateWith { pick(LABEL_VALUES.getValue(it)) }
}

fun newSeries(metric: MetricModel, labels: Map<String, String>? = null): SeriesModel {
    val chosen = labels ?: generateSequence { randomLabels() }.first { candidate -> metric.find(candidate) == null }
    val interval = weighted(*INTERVALS.map { it to if (it in listOf(1L, 7L, 128L)) 1 else 4 }.toTypedArray())
    val firstTime = maxOf(timeBase, now) + random.nextLong(0, interval * 3)
    return SeriesModel(chosen, interval, exact = chance(0.5), firstTime = firstTime).also { metric.series += it }
}

fun nextCounterValue(series: SeriesModel): Long {
    val last = series.values.lastOrNull() ?: return weighted(0L to 5, random.nextLong(1, 1_000) to 4, MAX_COUNTER to 1)
    if (last > 0 && chance(0.08)) return weighted(0L to 2, random.nextLong(0, last) to 3)
    val increment = weighted(0L to 3, random.nextLong(1, 40) to 8, random.nextLong(40, 5_000) to 2)
    return minOf(MAX_COUNTER, last + increment)
}

fun nextGaugeValue(series: SeriesModel): Long {
    val last = series.values.lastOrNull() ?: return weighted(0L to 3, random.nextLong(-100, 100) to 6, -MAX_COUNTER to 1)
    return weighted(last + random.nextLong(-40, 41) to 8, last to 2, 0L to 1, MAX_COUNTER to 1).coerceIn(-MAX_COUNTER, MAX_COUNTER)
}

fun newObservations(bucketCount: Int): List<Long> {
    val perBucket = LongArray(bucketCount)
    val observations = weighted(0 to 2, random.nextInt(1, 6) to 5, random.nextInt(6, 60) to 3, random.nextInt(60, 400) to 1)
    val typical = random.nextInt(bucketCount)
    repeat(observations) {
        perBucket[weighted(typical to 6, random.nextInt(bucketCount) to 3, bucketCount - 1 to 1)]++
    }
    return perBucket.toList()
}

fun restartedCounts(last: List<Long>): List<Long> {
    val perBucket = last.mapIndexed { bucket, count -> count - (last.getOrNull(bucket - 1) ?: 0L) }
    return cumulative(perBucket.map { count -> (count * random.nextDouble(0.0, 2.0)).toLong() + random.nextLong(0, 3) })
        .map { it.coerceAtMost(MAX_COUNTER) }
}

fun nextBucketCounts(metric: MetricModel, series: SeriesModel): List<Long> {
    val last = series.bucketCounts.lastOrNull()
    if (last == null) {
        return weighted(
            List(metric.bucketCount) { 0L } to 3,
            cumulative(newObservations(metric.bucketCount)) to 4,
            cumulative(listOf(MAX_COUNTER - random.nextLong(1_000, 5_000)) + List(metric.bounds.size) { random.nextLong(0, 5) }) to 1,
        )
    }
    if (chance(0.07)) return restartedCounts(last)
    val added = cumulative(newObservations(metric.bucketCount))
    return last.indices.map { bucket -> (last[bucket] + added[bucket]).coerceAtMost(MAX_COUNTER) }
}

fun advance(series: SeriesModel) {
    val jitter = if (series.exact) 0L else random.nextLong(-series.interval / 3, series.interval / 3 + 1)
    val pause = if (chance(0.04)) series.interval * random.nextLong(2, 8) else 0L
    series.nextTime = maxOf(series.lastTime!! + 1, series.nextTime + series.interval + jitter + pause)
}

fun valueText(metric: MetricModel, series: SeriesModel): String = when (metric.kind) {
    Kind.COUNTER -> nextCounterValue(series).also { series.values += it }.toString()
    Kind.GAUGE -> nextGaugeValue(series).also { series.values += it }.toString()
    Kind.HISTOGRAM -> nextBucketCounts(metric, series).also { series.bucketCounts += it }.joinToString(",")
}

fun addValid(metric: MetricModel, series: SeriesModel) {
    val time = series.nextTime
    if (time > MAX_TIME) return
    emitLine(spaced(listOf("ADD", time.toString(), valueText(metric, series), metric.name) + labelWords(series.labels)))
    series.times += time
    now = maxOf(now, time)
    advance(series)
}

fun dueSeries(): Pair<MetricModel, SeriesModel>? =
    metrics.flatMap { metric -> metric.series.map { metric to it } }.filter { it.second.nextTime <= MAX_TIME }.minByOrNull { it.second.nextTime }

fun addSample() {
    if (metrics.isEmpty()) return defineMetric(Kind.HISTOGRAM)
    val due = dueSeries()
    if (due != null && chance(0.75)) return addValid(due.first, due.second)
    val metric = pick(metrics)
    val series = if (metric.series.isEmpty() || chance(0.06)) newSeries(metric) else pick(metric.series)
    addValid(metric, series)
}

fun histogramMetrics(): List<MetricModel> = metrics.filter { it.kind == Kind.HISTOGRAM }

fun addOutOfOrder() {
    val candidates = metrics.flatMap { metric -> metric.series.filter { it.times.isNotEmpty() }.map { metric to it } }
    if (candidates.isEmpty()) return addSample()
    val (metric, series) = pick(candidates)
    val last = series.lastTime!!
    val time = weighted(last to 3, (last - random.nextLong(1, 30)).coerceAtLeast(0) to 2, series.times.first() to 1)
    val value = when (metric.kind) {
        Kind.COUNTER -> (series.values.last() + 1).coerceAtMost(MAX_COUNTER).toString()
        Kind.GAUGE -> "7"
        Kind.HISTOGRAM -> if (chance(0.7)) series.bucketCounts.last().joinToString(",") else badCountsText(metric.bucketCount)
    }
    emitLine(spaced(listOf("ADD", time.toString(), value, metric.name) + labelWords(series.labels)))
}

fun badCountsText(bucketCount: Int): String {
    val valid = cumulative(newObservations(bucketCount))
    val pieces = valid.map(Long::toString)
    return weighted(
        (pieces + "9").joinToString(",") to 3,
        (if (bucketCount > 1) pieces.dropLast(1) else pieces + pieces).joinToString(",") to 3,
        (if (bucketCount > 1) pieces.dropLast(1) + (valid[bucketCount - 2] - 1).toString() else listOf("2", "1")).joinToString(",") to 3,
        (listOf("5") + List(bucketCount - 1) { "4" }).joinToString(",") to 2,
        (pieces.take(1) + "" + pieces.drop(1)).joinToString(",") to 1,
        (pieces + "").joinToString(",") to 1,
        ("," + pieces.joinToString(",")) to 1,
        (listOf("-1") + pieces.drop(1)).joinToString(",") to 1,
        (listOf("1000000000001") + List(bucketCount - 1) { "1000000000001" }).joinToString(",") to 1,
        (listOf("01") + List(bucketCount - 1) { "2" }).joinToString(",") to 1,
        (if (bucketCount > 1) "3" else "1,2") to 1,
        (listOf("1.5") + List(bucketCount - 1) { "2" }).joinToString(",") to 1,
        List(bucketCount) { "a" }.joinToString(",") to 1,
        List(bucketCount) { "1" }.joinToString(";") to 1,
    )
}

fun addInvalid() {
    val metric = metrics.randomOrNull(random)
    val series = metric?.series?.randomOrNull(random)
    val labels = series?.let { labelWords(it.labels) } ?: listOf("job=api")
    val name = metric?.name ?: "http_request_duration_ms"
    val time = (series?.nextTime ?: timeBase).coerceAtMost(MAX_TIME).toString()
    val badValue = when (metric?.kind) {
        Kind.HISTOGRAM -> badCountsText(metric.bucketCount)
        Kind.GAUGE -> pick(BAD_GAUGE_VALUES)
        else -> pick(BAD_COUNTER_VALUES)
    }
    val line = weighted(
        listOf("ADD", time, badValue, name) + labels to 8,
        listOf("ADD", time, pick(listOf("1,2,3", "abc", "1,,2", "5")), pick(MISSING_NAMES)) + labels to 3,
        listOf("ADD", pick(BAD_TIMES), "1,2", name) + labels to 1,
        listOf("ADD", time, "0,0", pick(BAD_METRIC_NAMES)) + labels to 1,
        listOf("ADD", time, "0,0", name) + labels + pick(BAD_LABELS) to 2,
        listOf("ADD", time, badValue, name, "job=a", "job=a") to 1,
        listOf("ADD", time, "1,2") to 1,
        listOf("ADD") to 1,
    )
    emitLine(spaced(line))
}

class Window(val end: Long, val length: Long)

fun boundaryGap(sampled: Long, intervals: Int): Long {
    val exact = 11 * sampled
    val divisor = 10L * intervals
    return weighted(exact / divisor to 3, (exact + divisor - 1) / divisor to 2, maxOf(1, exact / divisor - 1) to 1)
}

fun craftedWindow(series: SeriesModel): Window {
    val times = series.times
    if (times.size < 2) return Window(times.firstOrNull() ?: timeBase, random.nextLong(1, 100))
    val last = random.nextInt(1, times.size)
    val first = weighted(random.nextInt(0, last) to 6, maxOf(0, last - random.nextInt(1, 4)) to 3, 0 to 2, last to 1)
    val lastTime = times[last]
    val firstTime = times[first]
    val intervals = last - first
    val nextTime = times.getOrNull(last + 1) ?: Long.MAX_VALUE
    val previousTime = times.getOrNull(first - 1) ?: Long.MIN_VALUE
    val end = weighted(
        lastTime to 4,
        (lastTime + random.nextLong(1, series.interval + 1)).coerceAtMost(nextTime - 1) to 3,
        (if (intervals > 0) lastTime + boundaryGap(lastTime - firstTime, intervals) else lastTime).coerceAtMost(nextTime - 1) to 3,
        (lastTime + random.nextLong(1, 5_000)) to 1,
    ).coerceAtMost(MAX_TIME)
    val start = weighted(
        firstTime to 3,
        firstTime - 1 to 3,
        (if (intervals > 0) firstTime - boundaryGap(lastTime - firstTime, intervals) else firstTime - 1).coerceAtLeast(previousTime) to 3,
        (firstTime - random.nextLong(1, series.interval * 2 + 1)).coerceAtLeast(previousTime) to 2,
        -random.nextLong(0, 50) to 1,
    )
    return Window(end, (end - start).coerceIn(1, MAX_TIME))
}

fun randomWindow(): Window {
    val end = weighted(now + random.nextLong(-200, 200) to 6, random.nextLong(0, 100) to 1, MAX_TIME to 1).coerceIn(0, MAX_TIME)
    val length = weighted(random.nextLong(1, 120) to 5, random.nextLong(120, 3_000) to 3, MAX_TIME to 1, 1L to 1)
    return Window(end, length)
}

fun selectorLabels(series: SeriesModel?): List<String> {
    if (series == null) return emptyList()
    val words = labelWords(series.labels)
    return weighted(
        emptyList<String>() to 3,
        words.take(1) to 4,
        words to 3,
        listOf("${pick(LABEL_NAMES.take(6))}=${pick(listOf("api", "b", "z9", "nope"))}") to 1,
        (words.take(1) + "zone=z9") to 1,
    ).distinctBy { it.substringBefore('=') }
}

fun randomPhi(): String = weighted(
    pick(PHI_COMMON) to 8,
    pick(PHI_RARE) to 4,
    "0.${random.nextInt(1, 1_000).toString().padStart(3, '0')}" to 2,
    "0.${random.nextInt(1, 10)}" to 1,
    "0.${random.nextInt(1, 100).toString().padStart(2, '0')}" to 1,
)

fun histogramQuery() {
    val histograms = histogramMetrics()
    val metric = when {
        histograms.isEmpty() || chance(0.06) -> metrics.randomOrNull(random)
        else -> pick(histograms)
    }
    val series = metric?.series?.filter { it.times.size >= 3 }?.randomOrNull(random) ?: metric?.series?.randomOrNull(random)
    val window = if (series != null && chance(0.9)) craftedWindow(series) else randomWindow()
    val name = if (metric == null || chance(0.03)) pick(MISSING_NAMES) else metric.name
    val selector = listOf(name) + selectorLabels(series)
    val head = listOf(window.end.toString(), window.length.toString())
    emitLine(spaced(if (chance(0.4)) listOf("BUCKETS") + head + selector else listOf("QUANTILE") + head + randomPhi() + selector))
}

fun counterQuery() {
    val metric = metrics.randomOrNull(random) ?: return
    val command = when (metric.kind) {
        Kind.COUNTER -> weighted("INCREASE" to 5, "RATE" to 3, "DELTA" to 1)
        Kind.GAUGE -> weighted("DELTA" to 5, "INCREASE" to 1)
        Kind.HISTOGRAM -> pick(listOf("INCREASE", "RATE", "DELTA"))
    }
    val series = metric.series.filter { it.times.size >= 3 }.randomOrNull(random) ?: metric.series.randomOrNull(random)
    val window = if (series != null) craftedWindow(series) else randomWindow()
    emitLine(spaced(listOf(command, window.end.toString(), window.length.toString(), metric.name) + selectorLabels(series)))
}

val SCENARIO_TOTALS = listOf(1L, 2L, 4L, 5L, 8L, 10L, 20L, 25L, 40L, 50L, 100L, 125L, 200L, 250L, 500L, 1_000L)

fun scenarioIncrements(bucketCount: Int, total: Long): List<Long> {
    val perBucket = LongArray(bucketCount)
    if (total == 0L) return perBucket.toList()
    val shape = weighted("one" to 2, "spread" to 4, "tail" to 2, "gap" to 2)
    var remaining = total
    when (shape) {
        "one" -> perBucket[random.nextInt(bucketCount)] = total
        "tail" -> {
            val tail = random.nextLong(1, total + 1)
            perBucket[bucketCount - 1] = tail
            remaining -= tail
            if (remaining > 0) perBucket[random.nextInt(bucketCount)] += remaining
        }
        "gap" -> {
            val low = random.nextInt(bucketCount)
            val high = random.nextInt(bucketCount)
            val lowShare = random.nextLong(0, total + 1)
            perBucket[low] += lowShare
            perBucket[high] += total - lowShare
        }
        else -> while (remaining > 0) {
            val share = random.nextLong(1, remaining + 1)
            perBucket[random.nextInt(bucketCount)] += share
            remaining -= share
        }
    }
    return perBucket.toList()
}

fun scenarioPhis(increments: List<Long>, total: Long): List<String> {
    if (total == 0L) return listOf(randomPhi())
    val exactRanks = cumulative(increments).dropLast(1).filter { it > 0 }.distinct().map { reached ->
        java.math.BigDecimal(reached).divide(java.math.BigDecimal(total)).stripTrailingZeros().toPlainString()
    }
    val choices = exactRanks.filter { it != "1" } + listOf("1", pick(PHI_COMMON), pick(PHI_RARE))
    return choices.shuffled(random).take(random.nextInt(2, 5))
}

fun scenario() {
    val metric = histogramMetrics().randomOrNull(random) ?: return defineMetric(Kind.HISTOGRAM)
    scenarioCount++
    val labels = mapOf("case" to "s$scenarioCount") + if (chance(0.3)) mapOf("job" to pick(LABEL_VALUES.getValue("job"))) else emptyMap()
    val series = newSeries(metric, labels)
    val start = (now + random.nextLong(1, 100)).coerceAtMost(MAX_TIME - 200)
    val sampled = pick(listOf(10L, 15L, 30L, 60L, 64L, 128L))
    val before = weighted(List(metric.bucketCount) { 0L } to 3, cumulative(newObservations(metric.bucketCount)) to 2)
    val total = weighted(0L to 1, pick(SCENARIO_TOTALS) to 9)
    val increments = scenarioIncrements(metric.bucketCount, total)
    val after = if (chance(0.2)) restartedCounts(before.map { it + 5 }) else before.zip(cumulative(increments), Long::plus)
    for ((time, counts) in listOf(start to before, start + sampled to after)) {
        emitLine(spaced(listOf("ADD", time.toString(), counts.joinToString(","), metric.name) + labelWords(labels)))
        series.times += time
        series.bucketCounts += counts
    }
    now = maxOf(now, start + sampled)
    series.nextTime = start + sampled + series.interval
    val end = start + sampled
    val length = weighted(2 * sampled to 4, sampled + 1 to 2, sampled * 11 / 10 + sampled to 1, sampled to 1)
    val extraLabels = labels.filterKeys { it != "case" }.map { "${it.key}=${it.value}" }.filter { chance(0.5) }
    val selector = listOf(metric.name) + (extraLabels + "case=s$scenarioCount").shuffled(random)
    emitLine(spaced(listOf("BUCKETS", end.toString(), length.toString()) + selector))
    for (phi in scenarioPhis(increments, total)) emitLine(spaced(listOf("QUANTILE", end.toString(), length.toString(), phi) + selector))
}

fun badQuery() {
    val metric = metrics.randomOrNull(random)
    val name = metric?.name ?: "rpc_duration_ms"
    val time = (now + random.nextLong(0, 500)).coerceAtMost(MAX_TIME).toString()
    val line = weighted(
        listOf("QUANTILE", time, "60", pick(BAD_PHIS), name) to 5,
        listOf("QUANTILE", time, "60", "0.5") to 2,
        listOf("QUANTILE", time, "60") to 1,
        listOf("BUCKETS", time, "60") to 2,
        listOf("BUCKETS", time) to 1,
        listOf("BUCKETS") to 1,
        listOf(pick(listOf("BUCKETS", "QUANTILE")), time, pick(BAD_LENGTHS), "0.5", name) to 1,
        listOf("BUCKETS", pick(BAD_TIMES), "60", name) to 1,
        listOf("BUCKETS", time, pick(BAD_LENGTHS), name) to 1,
        listOf("QUANTILE", time, "60", "0.5", pick(BAD_METRIC_NAMES)) to 1,
        listOf("BUCKETS", time, "60", name, pick(BAD_LABELS)) to 1,
        listOf("QUANTILE", time, "60", "0.9", name, "job=api", "job=api") to 1,
        listOf("QUANTILE", time, "60", pick(BAD_PHIS), pick(MISSING_NAMES)) to 1,
        listOf("BUCKETS", time, "60", pick(MISSING_NAMES)) to 1,
        listOf("QUANTILE", time, "60", name, "0.5") to 1,
    )
    emitLine(spaced(line))
}

fun badType() {
    val existing = metrics.randomOrNull(random)?.name ?: "rpc_duration_ms"
    val fresh = "fresh_${random.nextInt(100)}"
    val line = weighted(
        listOf("TYPE", existing, "histogram") + pick(BOUND_SETS).map(Long::toString) to 3,
        listOf("TYPE", existing, "histogram") + pick(BAD_BOUND_LISTS) to 2,
        listOf("TYPE", existing, pick(listOf("counter", "gauge"))) to 2,
        listOf("TYPE", fresh, "histogram") + pick(BAD_BOUND_LISTS) to 4,
        listOf("TYPE", fresh, "histogram") to 2,
        listOf("TYPE", pick(BAD_METRIC_NAMES), "histogram", "10") to 1,
        listOf("TYPE", fresh, pick(listOf("Histogram", "HISTOGRAM", "summary", "histograms"))) to 1,
        listOf("TYPE", fresh, pick(listOf("Histogram", "summary")), "10", "20") to 1,
        listOf("TYPE", fresh, "counter", "10") to 1,
        listOf("TYPE", fresh) to 1,
    )
    emitLine(spaced(line))
}

fun malformedLine() {
    val name = metrics.randomOrNull(random)?.name ?: "x"
    val line = weighted(
        "${pick(UNKNOWN_COMMANDS)} 10 60 $name" to 4,
        pick(UNKNOWN_COMMANDS) to 1,
        "BUCKETS 10 60 $name\r" to 1,
        "QUANTILE 10 60 0.5 $name\tjob=a" to 1,
        "\r" to 1,
    )
    emitLine(line)
}

fun runNormal() {
    timeBase = weighted(0L to 4, random.nextLong(1, 2_000) to 3, MAX_TIME - random.nextLong(30_000, 60_000) to 1)
    repeat(random.nextInt(1, 3)) { defineMetric(Kind.HISTOGRAM) }
    if (chance(0.8)) defineMetric(Kind.COUNTER)
    if (chance(0.5)) defineMetric(Kind.GAUGE)
    for (metric in metrics) repeat(weighted(0 to 2, 1 to 3, 2 to 1)) { newSeries(metric) }
    while (emittedLines < options.lines) {
        val warmingUp = emittedLines < options.lines / 6
        when (weighted(
            "add" to if (warmingUp) 200 else 40,
            "histogram-query" to 22,
            "counter-query" to 5,
            "scenario" to 3,
            "out-of-order" to 3,
            "bad-add" to 6,
            "bad-query" to 5,
            "type" to 2,
            "bad-type" to 3,
            "malformed" to 2,
            "blank" to 1,
        )) {
            "add" -> addSample()
            "histogram-query" -> histogramQuery()
            "counter-query" -> counterQuery()
            "scenario" -> scenario()
            "out-of-order" -> addOutOfOrder()
            "bad-add" -> addInvalid()
            "bad-query" -> badQuery()
            "type" -> if (metrics.size < 7) defineMetric(weighted(Kind.HISTOGRAM to 3, Kind.COUNTER to 1, Kind.GAUGE to 1)) else badType()
            "bad-type" -> badType()
            "malformed" -> malformedLine()
            else -> emitLine(pick(listOf("", " ", "   ")))
        }
    }
}

val LARGE_BOUNDS = listOf(5L, 10L, 25L, 50L, 100L, 250L, 500L, 1_000L, 2_500L, 5_000L)
val BIG_SERIES = 2
val SMALL_SAMPLES = 3
val BIG_INTERVAL = 15L
val ROUTES = 10
val ROUTE_QUERIES = 50

class LargeHistogram(val labels: String, val big: Boolean) {
    val perBucket = LongArray(LARGE_BOUNDS.size + 1)
    var lastTime = -1L
}

fun largeHistogram(index: Int): LargeHistogram =
    if (index < BIG_SERIES) LargeHistogram("pod=api-${index + 1}", big = true) else LargeHistogram("pod=p$index route=r${index % ROUTES}", big = false)

fun nextLargeCounts(series: LargeHistogram): String {
    if (!series.big && series.perBucket.sum() > 0 && random.nextInt(30) == 0) series.perBucket.fill(0)
    if (series.big && random.nextInt(8_000) == 0) series.perBucket.fill(0)
    val observations = if (series.big) random.nextInt(0, 12) else random.nextInt(0, 40)
    repeat(observations) {
        val bucket = (random.nextDouble() * random.nextDouble() * series.perBucket.size).toInt()
        series.perBucket[bucket]++
    }
    return series.perBucket.toList().runningReduce(Long::plus).joinToString(",")
}

fun runLarge() {
    val smallCount = options.series - BIG_SERIES
    val bigSamples = (options.samples - smallCount * SMALL_SAMPLES) / BIG_SERIES
    val span = bigSamples * BIG_INTERVAL
    val series = List(options.series, ::largeHistogram)
    emit("TYPE request_duration_ms histogram ${LARGE_BOUNDS.joinToString(" ")}")
    emit("TYPE requests_total counter")

    val events = LongArray(bigSamples * BIG_SERIES + smallCount * SMALL_SAMPLES)
    var eventCount = 0
    for (index in 0 until BIG_SERIES) {
        for (sample in 0 until bigSamples) events[eventCount++] = (sample * BIG_INTERVAL + index) * 65_536 + index
    }
    for (index in BIG_SERIES until options.series) {
        val interval = span / SMALL_SAMPLES
        var time = random.nextLong(0, interval)
        repeat(SMALL_SAMPLES) {
            events[eventCount++] = time * 65_536 + index
            time += interval - random.nextLong(0, interval / 4 + 1)
        }
    }
    events.sort(0, eventCount)

    val interleavedQueries = options.queries / 25
    val queryEvery = eventCount / interleavedQueries
    for (position in 0 until eventCount) {
        val time = events[position] / 65_536
        val target = series[(events[position] % 65_536).toInt()]
        if (time <= target.lastTime) continue
        target.lastTime = time
        emit("ADD $time ${nextLargeCounts(target)} request_duration_ms ${target.labels}")
        if (position % queryEvery == queryEvery - 1) emitLargeQuery(time, early = true)
    }
    repeat(options.queries - interleavedQueries) { emitLargeQuery(span, early = false) }
}

fun emitLargeQuery(now: Long, early: Boolean) {
    val loaded = now
    val head = if (chance(0.45)) "BUCKETS" else "QUANTILE"
    fun command(end: Long, length: Long): String =
        if (head == "BUCKETS") "BUCKETS $end $length" else "QUANTILE $end $length ${weighted(pick(PHI_COMMON) to 8, pick(PHI_RARE) to 2)}"
    if (random.nextInt(options.queries) < ROUTE_QUERIES) {
        emit("${command(now, loaded * random.nextLong(70, 101) / 100 + 1)} request_duration_ms route=r${random.nextInt(ROUTES)}")
        return
    }
    when (weighted("big" to if (early) 10 else 90, "single" to 7, "rare" to 1)) {
        "big" -> {
            val end = now - random.nextLong(0, minOf(loaded / 20 + 1, 50_000))
            val length = (end * random.nextLong(85, 101) / 100 + random.nextLong(0, 30)).coerceAtLeast(1)
            emit("${command(end, length)} request_duration_ms pod=api-${random.nextInt(1, BIG_SERIES + 1)}")
        }
        "single" -> {
            val length = weighted(random.nextLong(loaded / 5 + 1, loaded + 2) to 6, random.nextLong(1, 600) to 1)
            emit("${command(now, length)} request_duration_ms pod=p${random.nextInt(BIG_SERIES, options.series)}")
        }
        else -> emit(
            weighted(
                "QUANTILE $now 60 0.5 requests_total" to 1,
                "INCREASE $now 60 request_duration_ms pod=api-1" to 1,
                "QUANTILE $now 60 0 request_duration_ms pod=api-1" to 1,
                "BUCKETS $now 60 missing_ms" to 1,
                "BUCKETS $now 10 request_duration_ms pod=nope" to 2,
                "QUANTILE $now 1 0.99 request_duration_ms pod=api-2" to 2,
            ),
        )
    }
}

if (options.mode == "large") runLarge() else runNormal()
output.flush()
