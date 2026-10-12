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
        "--series" to "50000",
        "--samples" to "800000",
        "--queries" to "250000",
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
    require(options.series in 1_000..60_000) { "--series 는 1,000~60,000 이다" }
    require(options.samples in options.series * 3..1_000_000) { "--samples 는 시계열 수의 세 배 이상 1,000,000 이하다" }
    require(options.queries in 1_000..300_000) { "--queries 는 1,000~300,000 이다" }
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

val MAX_TIME = 1_000_000_000L
val MAX_COUNTER = 1_000_000_000_000L

enum class Kind(val word: String) { COUNTER("counter"), GAUGE("gauge") }

class SeriesModel(val labels: Map<String, String>, val interval: Long, val exact: Boolean, firstTime: Long) {
    val times = ArrayList<Long>()
    val values = ArrayList<Long>()
    var nextTime = firstTime
    val lastTime: Long? get() = times.lastOrNull()
}

class MetricModel(val name: String, val kind: Kind) {
    val series = ArrayList<SeriesModel>()

    fun find(labels: Map<String, String>): SeriesModel? = series.firstOrNull { it.labels == labels }
}

val COUNTER_NAMES = listOf(
    "http_requests_total", "errors_total", "jobs_total", "bytes_sent_total", "logins_total", "a", "_x", ":ratio:sum",
    "Rpc_Calls_total", "c".repeat(64),
)
val GAUGE_NAMES = listOf("queue_length", "temperature", "inflight", "memory_bytes", "g", "Up", "z:z")
val MISSING_NAMES = listOf("missing", "nope", "http_requests", "Queue_length", "errors_total_x")
val BAD_METRIC_NAMES = listOf("9lives", "-a", "a-b", "a.b", "m".repeat(65), "é", "a/b", "{x}", "1")
val LABEL_NAMES = listOf("job", "instance", "zone", "method", "code", "path", "_", "le", "Job", "x".repeat(64))
val LABEL_VALUES = mapOf(
    "job" to listOf("api", "batch", "web"),
    "instance" to listOf("a", "b", "c", "10.0.0.1:8080", "10.0.0.2:8080"),
    "zone" to listOf("z1", "z2"),
    "method" to listOf("GET", "POST"),
    "code" to listOf("200", "500", "503"),
    "path" to listOf("/v1/users", "/", "-", "a.b:c/d_e"),
    "_" to listOf("_", "0"),
    "le" to listOf("0.5", "Inf"),
    "Job" to listOf("API"),
    "x".repeat(64) to listOf("v".repeat(64)),
)
val BAD_LABELS = listOf(
    "job", "job=", "=api", "job==api", "job=a=b", "9job=api", "jo-b=api", "job=é", "job=a,b", "job=(x)",
    "j".repeat(65) + "=a", "job=" + "v".repeat(65), "job=a*", "job=\"api\"",
)
val BAD_TIMES = listOf("-1", "01", "1000000001", "1.5", "+3", "1e3", "9223372036854775808", "t", "00")
val BAD_LENGTHS = listOf("0", "-5", "01", "1000000001", "1.0", "x", "99999999999999999999")
val BAD_COUNTER_VALUES = listOf("-1", "-5", "1000000000001", "01", "1.5", "abc", "1e3", "+3", "9223372036854775808", "0x10", "-0", "1,2")
val BAD_GAUGE_VALUES = listOf("-0", "1000000000001", "-1000000000001", "+5", "1.0", "--1", "0-", "-01", "x", "1,2")
val UNKNOWN_COMMANDS = listOf(
    "add", "Add", "type", "Increase", "INCREASES", "RATES", "rate", "delta", "DEL", "QUERY", "SUM", "INC", "ERR",
    "NONE", "-", "ADD\t10", "TYPE\tx",
)
val INTERVALS = listOf(10L, 15L, 20L, 30L, 60L, 1L, 7L, 128L)

val metrics = ArrayList<MetricModel>()
var timeBase = 0L
var now = 0L
var emittedLines = 0

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

fun defineMetric(kind: Kind) {
    val pool = if (kind == Kind.COUNTER) COUNTER_NAMES else GAUGE_NAMES
    val unused = pool.filter { name -> metrics.none { it.name == name } }
    if (unused.isEmpty()) return
    val metric = MetricModel(pick(unused), kind)
    emitLine(spaced(listOf("TYPE", metric.name, kind.word)))
    metrics += metric
    newSeries(metric)
}

fun randomLabels(): Map<String, String> {
    val count = weighted(0 to 1, 1 to 4, 2 to 6, 3 to 2)
    val names = LABEL_NAMES.take(6).shuffled(random).take(count) + listOfNotNull(LABEL_NAMES.drop(6).randomOrNull(random)?.takeIf { chance(0.15) })
    return names.distinct().associateWith { pick(LABEL_VALUES.getValue(it)) }
}

fun newSeries(metric: MetricModel): SeriesModel {
    val labels = generateSequence { randomLabels() }.first { candidate -> metric.find(candidate) == null }
    val interval = weighted(*INTERVALS.map { it to if (it in listOf(1L, 7L, 128L)) 1 else 4 }.toTypedArray())
    val firstTime = maxOf(timeBase, now) + random.nextLong(0, interval * 3)
    return SeriesModel(labels, interval, exact = chance(0.5), firstTime = firstTime).also { metric.series += it }
}

fun nextCounterValue(series: SeriesModel): Long {
    val last = series.values.lastOrNull() ?: return weighted(0L to 5, random.nextLong(1, 1_000) to 4, MAX_COUNTER to 1)
    if (last > 0 && chance(0.08)) return weighted(0L to 2, random.nextLong(0, last) to 3)
    val increment = weighted(
        0L to 3,
        random.nextLong(1, 40) to 8,
        random.nextLong(40, 5_000) to 2,
        random.nextLong(0, MAX_COUNTER - last + 1) to 1,
    )
    return minOf(MAX_COUNTER, last + increment)
}

fun nextGaugeValue(series: SeriesModel): Long {
    val last = series.values.lastOrNull() ?: return weighted(0L to 3, random.nextLong(-100, 100) to 6, -MAX_COUNTER to 1, MAX_COUNTER to 1)
    val next = weighted(
        last + random.nextLong(-40, 41) to 8,
        last to 2,
        0L to 1,
        -MAX_COUNTER to 1,
        MAX_COUNTER to 1,
        random.nextLong(-1_000_000, 1_000_000) to 1,
    )
    return next.coerceIn(-MAX_COUNTER, MAX_COUNTER)
}

fun advance(series: SeriesModel) {
    val jitter = if (series.exact) 0L else random.nextLong(-series.interval / 3, series.interval / 3 + 1)
    val pause = if (chance(0.04)) series.interval * random.nextLong(2, 8) else 0L
    series.nextTime = maxOf(series.lastTime!! + 1, series.nextTime + series.interval + jitter + pause)
}

fun addValid(metric: MetricModel, series: SeriesModel) {
    val time = series.nextTime
    if (time > MAX_TIME) return
    val value = if (metric.kind == Kind.COUNTER) nextCounterValue(series) else nextGaugeValue(series)
    emitLine(spaced(listOf("ADD", time.toString(), value.toString(), metric.name) + labelWords(series.labels)))
    series.times += time
    series.values += value
    now = maxOf(now, time)
    advance(series)
}

fun dueSeries(): Pair<MetricModel, SeriesModel>? =
    metrics.flatMap { metric -> metric.series.map { metric to it } }.filter { it.second.nextTime <= MAX_TIME }.minByOrNull { it.second.nextTime }

fun addSample() {
    if (metrics.isEmpty()) return defineMetric(Kind.COUNTER)
    val due = dueSeries()
    if (due != null && chance(0.75)) return addValid(due.first, due.second)
    val metric = pick(metrics)
    val series = if (metric.series.isEmpty() || chance(0.06)) newSeries(metric) else pick(metric.series)
    addValid(metric, series)
}

fun addOutOfOrder() {
    val candidates = metrics.flatMap { metric -> metric.series.filter { it.times.isNotEmpty() }.map { metric to it } }
    if (candidates.isEmpty()) return addSample()
    val (metric, series) = pick(candidates)
    val last = series.lastTime!!
    val time = weighted(last to 3, (last - random.nextLong(1, 30)).coerceAtLeast(0) to 2, series.times.first() to 1)
    val value = if (metric.kind == Kind.COUNTER) (series.values.last() + 1).coerceAtMost(MAX_COUNTER) else 7L
    emitLine(spaced(listOf("ADD", time.toString(), value.toString(), metric.name) + labelWords(series.labels)))
}

fun addInvalid() {
    val metric = metrics.randomOrNull(random)
    val series = metric?.series?.randomOrNull(random)
    val labels = series?.let { labelWords(it.labels) } ?: listOf("job=api")
    val name = metric?.name ?: "http_requests_total"
    val time = (series?.nextTime ?: timeBase).coerceAtMost(MAX_TIME).toString()
    val badValue = if (metric?.kind == Kind.GAUGE) pick(BAD_GAUGE_VALUES) else pick(BAD_COUNTER_VALUES)
    val line = weighted(
        listOf("ADD", time, badValue, name) + labels to 6,
        listOf("ADD", time, pick(listOf("5", "abc", "-5", "1,2")), pick(MISSING_NAMES)) + labels to 3,
        listOf("ADD", pick(BAD_TIMES), "5", name) + labels to 2,
        listOf("ADD", time, "5", pick(BAD_METRIC_NAMES)) + labels to 2,
        listOf("ADD", time, "5", name) + labels + pick(BAD_LABELS) to 3,
        listOf("ADD", time, badValue, name, "job=a", "job=a") to 1,
        listOf("ADD", time, "5", name, "job=a", "job=b") to 2,
        listOf("ADD", time, "5") to 1,
        listOf("ADD", time) to 1,
        listOf("ADD") to 1,
        listOf("ADD", "1000000001", badValue, pick(MISSING_NAMES)) to 1,
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
    val first = weighted(
        random.nextInt(0, last) to 6,
        maxOf(0, last - random.nextInt(1, 4)) to 3,
        0 to 2,
        last to 1,
    )
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
    val length = (end - start).coerceIn(1, MAX_TIME)
    return Window(end, length)
}

fun randomWindow(): Window {
    val end = weighted(
        now + random.nextLong(-200, 200) to 6,
        random.nextLong(0, 100) to 1,
        MAX_TIME to 1,
    ).coerceIn(0, MAX_TIME)
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

fun query() {
    val wantCounter = chance(0.65)
    val matching = metrics.filter { (it.kind == Kind.COUNTER) == wantCounter }
    val metric = when {
        matching.isEmpty() || chance(0.05) -> metrics.randomOrNull(random)
        else -> pick(matching)
    }
    val command = when {
        metric == null -> pick(listOf("INCREASE", "RATE", "DELTA"))
        metric.kind == Kind.COUNTER -> weighted("INCREASE" to 6, "RATE" to 4, "DELTA" to 1)
        else -> weighted("DELTA" to 8, "INCREASE" to 1, "RATE" to 1)
    }
    val series = metric?.series?.filter { it.times.size >= 3 }?.randomOrNull(random) ?: metric?.series?.randomOrNull(random)
    val window = if (series != null && chance(0.9)) craftedWindow(series) else randomWindow()
    val name = if (metric == null || chance(0.04)) pick(MISSING_NAMES) else metric.name
    val labels = if (metric == null) emptyList() else selectorLabels(series)
    emitLine(spaced(listOf(command, window.end.toString(), window.length.toString(), name) + labels))
}

val TIE_SPAN = 128L
var tieCases = 0

fun oddChange(): Long = random.nextLong(0, 400) * 2 + 1

fun roundingTie() {
    val metric = metrics.randomOrNull(random) ?: return defineMetric(Kind.COUNTER)
    tieCases++
    val labels = mapOf("case" to "t$tieCases")
    val start = (maxOf(timeBase, now) + random.nextLong(1, 100)).coerceAtMost(MAX_TIME - TIE_SPAN)
    val end = start + TIE_SPAN
    val change = if (chance(0.85)) oddChange() else oddChange() + 1
    val first: Long
    val second: Long
    if (metric.kind == Kind.COUNTER) {
        first = weighted(random.nextLong(0, 1_000) to 4, MAX_COUNTER - random.nextLong(1_000, 2_000) to 1)
        second = if (first > change && chance(0.3)) change else first + change
    } else {
        first = random.nextLong(-1_000, 1_000)
        second = if (chance(0.5)) first + change else first - change
    }
    val series = SeriesModel(labels, TIE_SPAN, exact = true, firstTime = start).also { metric.series += it }
    for ((time, value) in listOf(start to first, end to second)) {
        emitLine(spaced(listOf("ADD", time.toString(), value.toString(), metric.name) + labelWords(labels)))
        series.times += time
        series.values += value
    }
    series.nextTime = end + TIE_SPAN
    now = maxOf(now, end)
    val selector = listOf(metric.name, "case=t$tieCases")
    if (metric.kind == Kind.COUNTER) {
        emitLine(spaced(listOf("INCREASE", end.toString(), (TIE_SPAN + 1).toString()) + selector))
        emitLine(spaced(listOf("RATE", end.toString(), (TIE_SPAN * 2).toString()) + selector))
    } else {
        emitLine(spaced(listOf("DELTA", end.toString(), (TIE_SPAN + 1).toString()) + selector))
    }
}

fun badQuery() {
    val metric = metrics.randomOrNull(random)
    val name = metric?.name ?: "errors_total"
    val command = pick(listOf("INCREASE", "RATE", "DELTA"))
    val time = (timeBase + random.nextLong(0, 500)).toString()
    val line = weighted(
        listOf(command, time, pick(BAD_LENGTHS), name) to 3,
        listOf(command, pick(BAD_TIMES), "60", name) to 2,
        listOf(command, time, "60") to 2,
        listOf(command, time) to 1,
        listOf(command) to 1,
        listOf(command, time, "60", pick(BAD_METRIC_NAMES)) to 2,
        listOf(command, time, "60", name, pick(BAD_LABELS)) to 2,
        listOf(command, time, "60", name, "job=api", "job=api") to 1,
        listOf(command, time, "60", name, "job=api", "job=web") to 1,
        listOf(command, time, pick(BAD_LENGTHS), pick(MISSING_NAMES)) to 1,
    )
    emitLine(spaced(line))
}

fun badType() {
    val existing = metrics.randomOrNull(random)?.name ?: "jobs_total"
    val line = weighted(
        listOf("TYPE", existing, pick(listOf("counter", "gauge"))) to 4,
        listOf("TYPE", "fresh_${random.nextInt(100)}", pick(listOf("summary", "Counter", "GAUGE", "count", "untyped", "-"))) to 3,
        listOf("TYPE", pick(BAD_METRIC_NAMES), "counter") to 2,
        listOf("TYPE", "fresh_${random.nextInt(100)}") to 1,
        listOf("TYPE") to 1,
        listOf("TYPE", "fresh_${random.nextInt(100)}", "counter", "extra") to 1,
        listOf("TYPE", pick(BAD_METRIC_NAMES), "summary") to 1,
    )
    emitLine(spaced(line))
}

fun malformedLine() {
    val line = weighted(
        "${pick(UNKNOWN_COMMANDS)} 10 60 x" to 4,
        pick(UNKNOWN_COMMANDS) to 1,
        "INCREASE 10 60 ${metrics.randomOrNull(random)?.name ?: "x"}\r" to 1,
        "ADD 10 5 ${metrics.randomOrNull(random)?.name ?: "x"}\tjob=a" to 1,
        "\r" to 1,
        "\t" to 1,
    )
    emitLine(line)
}

fun runNormal() {
    timeBase = weighted(0L to 4, random.nextLong(1, 2_000) to 3, 1_000L to 2, MAX_TIME - random.nextLong(30_000, 60_000) to 1)
    repeat(random.nextInt(1, 4)) { defineMetric(Kind.COUNTER) }
    repeat(random.nextInt(1, 3)) { defineMetric(Kind.GAUGE) }
    for (metric in metrics) repeat(weighted(0 to 2, 1 to 3, 2 to 1)) { newSeries(metric) }
    while (emittedLines < options.lines) {
        when (weighted(
            "add" to 44,
            "query" to 30,
            "rounding-tie" to 2,
            "out-of-order" to 3,
            "bad-add" to 5,
            "bad-query" to 4,
            "type" to 2,
            "bad-type" to 2,
            "malformed" to 3,
            "blank" to 1,
        )) {
            "add" -> addSample()
            "query" -> query()
            "rounding-tie" -> roundingTie()
            "out-of-order" -> addOutOfOrder()
            "bad-add" -> addInvalid()
            "bad-query" -> badQuery()
            "type" -> if (metrics.size < 7) defineMetric(if (chance(0.6)) Kind.COUNTER else Kind.GAUGE) else badType()
            "bad-type" -> badType()
            "malformed" -> malformedLine()
            else -> emitLine(pick(listOf("", " ", "   ")))
        }
    }
}

val LARGE_START = 0L
val BIG_INTERVAL = 15L
val BIG_SERIES = 3
val SMALL_SAMPLES = 2
val ZONES = 20
val ZONE_QUERIES = 400

class LargeSeries(val metric: String, val labels: String, val counter: Boolean, val big: Boolean) {
    var value = 0L
    var lastTime = -1L
}

val requestEnd = options.series * 84 / 100
val queueEnd = requestEnd + options.series * 12 / 100

fun largeSeries(index: Int): LargeSeries = when {
    index < BIG_SERIES -> LargeSeries("requests_total", "job=edge pod=edge-${index + 1}", counter = true, big = true)
    index < requestEnd -> LargeSeries("requests_total", "pod=p$index zone=z${index % ZONES}", counter = true, big = false)
    index < queueEnd -> LargeSeries("queue_depth", "queue=q$index zone=z${index % ZONES}", counter = false, big = false)
    else -> LargeSeries("errors_total", "pod=p$index code=${weighted("500" to 3, "503" to 1)}", counter = true, big = false)
}

fun nextLargeValue(series: LargeSeries): Long {
    if (!series.counter) {
        series.value = (series.value + random.nextLong(-30, 31)).coerceIn(-MAX_COUNTER, MAX_COUNTER)
        return series.value
    }
    val restart = series.value > 0 && random.nextInt(if (series.big) 5_000 else 40) == 0
    series.value = if (restart) random.nextLong(0, minOf(series.value, 500)) else series.value + random.nextLong(0, if (series.big) 60 else 400)
    return series.value
}

fun runLarge() {
    val smallCount = options.series - BIG_SERIES
    val bigSamples = (options.samples - smallCount * SMALL_SAMPLES) / BIG_SERIES
    val span = bigSamples * BIG_INTERVAL
    val series = List(options.series, ::largeSeries)
    emit("TYPE requests_total counter")
    emit("TYPE queue_depth gauge")
    emit("TYPE errors_total counter")

    val events = LongArray(bigSamples * BIG_SERIES + smallCount * SMALL_SAMPLES)
    var eventCount = 0
    for (index in 0 until BIG_SERIES) {
        for (sample in 0 until bigSamples) events[eventCount++] = (LARGE_START + sample * BIG_INTERVAL + index) * 65_536 + index
    }
    for (index in BIG_SERIES until options.series) {
        val interval = span / SMALL_SAMPLES
        var time = LARGE_START + random.nextLong(0, interval)
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
        emit("ADD $time ${nextLargeValue(target)} ${target.metric} ${target.labels}")
        if (position % queryEvery == queryEvery - 1) emitLargeQuery(time, early = true)
    }
    repeat(options.queries - interleavedQueries) { emitLargeQuery(LARGE_START + span, early = false) }
}

fun emitLargeQuery(now: Long, early: Boolean) {
    val loaded = now - LARGE_START
    if (random.nextInt(options.queries) < ZONE_QUERIES) {
        val length = loaded * random.nextLong(70, 101) / 100 + 1
        if (chance(0.75)) {
            emit("${if (chance(0.5)) "INCREASE" else "RATE"} $now $length requests_total zone=z${random.nextInt(ZONES)}")
        } else {
            emit("DELTA $now $length queue_depth zone=z${random.nextInt(ZONES)}")
        }
        return
    }
    when (weighted("big" to if (early) 10 else 88, "single" to 6, "gauge" to 4, "errors" to 1, "rare" to 1)) {
        "big" -> {
            val end = now - random.nextLong(0, minOf(loaded / 20 + 1, 50_000))
            val length = (end - LARGE_START) * random.nextLong(80, 101) / 100 + random.nextLong(0, 30)
            val command = if (chance(0.6)) "INCREASE" else "RATE"
            val pod = "pod=edge-${random.nextInt(1, BIG_SERIES + 1)}"
            emit("$command $end ${length.coerceAtLeast(1)} requests_total ${if (chance(0.7)) pod else "job=edge $pod"}")
        }
        "single" -> {
            val length = weighted(random.nextLong(loaded / 5 + 1, loaded + 2) to 6, loaded + 1 to 1, random.nextLong(1, 600) to 1)
            emit("${if (chance(0.6)) "INCREASE" else "RATE"} $now $length requests_total pod=p${random.nextInt(BIG_SERIES, requestEnd)}")
        }
        "gauge" -> emit("DELTA $now ${random.nextLong(loaded / 5 + 1, loaded + 2)} queue_depth queue=q${random.nextInt(requestEnd, queueEnd)}")
        "errors" -> {
            val command = if (chance(0.5)) "INCREASE" else "RATE"
            emit("$command $now ${random.nextLong(loaded / 5 + 1, loaded + 2)} errors_total pod=p${random.nextInt(queueEnd, options.series)}")
        }
        else -> emit(
            weighted(
                "INCREASE $now 60 queue_depth" to 1,
                "DELTA $now 60 requests_total pod=edge-1" to 1,
                "RATE $now 0 requests_total" to 1,
                "INCREASE $now 60 missing_total" to 1,
                "INCREASE $now 10 requests_total pod=nope" to 2,
                "RATE $now 5 errors_total code=503 pod=p1" to 1,
                "INCREASE $now 1 requests_total job=edge pod=edge-2" to 2,
            ),
        )
    }
}

if (options.mode == "large") runLarge() else runNormal()
output.flush()
