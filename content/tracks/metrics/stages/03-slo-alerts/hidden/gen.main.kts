import kotlin.random.Random

class Options(
    val mode: String,
    val lines: Int,
    val series: Int,
    val days: Int,
    val evaluations: Int,
    val burns: Int,
    val seed: Long,
)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--mode" to "normal",
        "--lines" to "400",
        "--series" to "20000",
        "--days" to "4",
        "--evaluations" to "50000",
        "--burns" to "3000",
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
        days = values.getValue("--days").toInt(),
        evaluations = values.getValue("--evaluations").toInt(),
        burns = values.getValue("--burns").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.mode == "normal" || options.mode == "large") { "--mode 는 normal 이나 large 다" }
    require(options.lines in 10..100_000) { "--lines 는 10~100,000 이다" }
    require(options.series in 1_000..40_000) { "--series 는 1,000~40,000 이다" }
    require(options.days in 4..6) { "--days 는 4~6 이다" }
    require(options.evaluations in 1_000..60_000) { "--evaluations 는 1,000~60,000 이다" }
    require(options.burns in 0..20_000) { "--burns 는 0~20,000 이다" }
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
val MAX_EVALUATION_TIMES = 200L

class CounterModel(val metric: String, val labels: String) {
    val times = ArrayList<Long>()
    var value = 0L
}

class PodModel(val service: String, val pod: String) {
    lateinit var requests: CounterModel
    var errors: CounterModel? = null
}

class ServiceModel(val name: String, val requestRate: Long, val steady: Boolean) {
    val pods = ArrayList<PodModel>()
    var errorRate = 0L
    var incidentSteps = 0
    var idleSteps = 0
}

val REQUEST_METRICS = listOf("api_requests_total", "req_total")
val ERROR_METRICS = listOf("api_errors_total", "err_total")
val SERVICE_NAMES = listOf("checkout", "search", "web", "Cart", "pay-1", "a.b:c/d")
val STEADY_ERROR_RATES = listOf(6L, 3L, 60L, 30L)
val OBJECTIVES = listOf("99", "99.9", "99.5", "95", "99.95", "90", "0.5", "99.999", "50", "1.5")
val THRESHOLDS = listOf("14.4", "6", "1", "3", "10", "2", "0.5", "1.5", "100000", "0.001", "99999.999")
val RULE_NAME_ALPHABET = "abcABZ09._:-"
val BAD_RULE_NAMES = listOf("api/v1", "a\tb", "x".repeat(65), "é", "a,b", "(x)", "a=b", "*")
val BAD_OBJECTIVES = listOf("100", "0", "0.000", "99.9999", "099", "100.0", "-1", "1e2", ".5", "99.", "+99", "abc", "101")
val BAD_THRESHOLDS = listOf("0", "6.0001", ".5", "100000.001", "1000000", "-1", "1e3", "06", "0.0", "abc", "14,4")
val BAD_LENGTHS = listOf("0", "-5", "01", "1000000001", "1.0", "x")
val BAD_TIMES = listOf("-1", "01", "1000000001", "1.5", "t")
val UNKNOWN_COMMANDS = listOf(
    "slo", "Slo", "SLOS", "burn", "Burn", "BURNRATE", "alert", "ALERTS", "eval", "Eval", "EVALUATE", "FIRING", "RESOLVED",
    "EVALUATED", "RULE", "SLO\tx", "EVAL\t1",
)

val services = ArrayList<ServiceModel>()
val sloNames = ArrayList<String>()
val alertNames = ArrayList<String>()
val definedMetrics = ArrayList<Pair<String, String>>()
var step = 30L
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

fun freshRuleName(taken: List<String>): String {
    val common = listOf("page", "ticket", "fast-burn", "slow-burn", "api", "checkout", "B-alert", "a-alert", "9", "_x", "A.b:c")
    val unused = common.filter { it !in taken }
    if (unused.isNotEmpty() && chance(0.7)) return pick(unused)
    return generateSequence {
        String(CharArray(weighted(1 to 1, random.nextInt(2, 9) to 6, 64 to 1)) { RULE_NAME_ALPHABET[random.nextInt(RULE_NAME_ALPHABET.length)] })
    }.first { it !in taken }
}

fun defineMetric(name: String, kind: String, extra: List<String> = emptyList()) {
    emitLine(spaced(listOf("TYPE", name, kind) + extra))
    definedMetrics += name to kind
}

fun addSample(counter: CounterModel, time: Long, increment: Long, steady: Boolean) {
    val restart = !steady && counter.value > 0 && chance(0.01)
    counter.value = if (restart) random.nextLong(0, minOf(counter.value, 50)) else minOf(MAX_COUNTER, counter.value + increment)
    counter.times += time
    emitLine(spaced(listOf("ADD", time.toString(), counter.value.toString(), counter.metric, counter.labels)))
}

fun scrape(time: Long) {
    for (service in services) {
        if (!service.steady) {
            if (service.incidentSteps > 0) service.incidentSteps-- else if (chance(0.06)) service.incidentSteps = random.nextInt(2, 12)
            if (service.idleSteps > 0) service.idleSteps-- else if (chance(0.02)) service.idleSteps = random.nextInt(3, 8)
            service.errorRate = when {
                service.idleSteps > 0 -> 0L
                service.incidentSteps > 0 -> service.requestRate * random.nextLong(1, 20) / 100
                else -> weighted(0L to 12, 1L to 1)
            }
        } else if (chance(0.02)) {
            service.errorRate = if (service.errorRate == 0L) pick(STEADY_ERROR_RATES) else 0L
        }
        for (pod in service.pods) {
            if (!service.steady && chance(0.05)) continue
            val requests = when {
                service.steady -> service.requestRate
                service.idleSteps > 0 -> 0L
                else -> service.requestRate + random.nextLong(-5, 6)
            }
            addSample(pod.requests, time, requests, service.steady)
            val errors = pod.errors ?: if (service.errorRate > 0 || chance(0.1)) {
                CounterModel(pick(ERROR_METRICS.filter { metric -> definedMetrics.any { it.first == metric } }), pod.requests.labels).also { pod.errors = it }
            } else {
                null
            }
            if (errors != null) addSample(errors, time, service.errorRate, service.steady)
        }
    }
}

fun defineSlo() {
    val name = freshRuleName(sloNames)
    val service = pick(services)
    val totalMetric = service.pods.first().requests.metric
    val badMetric = weighted(pick(ERROR_METRICS.filter { metric -> definedMetrics.any { it.first == metric } }) to 8, totalMetric to 1)
    val labels = weighted(listOf("service=${service.name}") to 6, emptyList<String>() to 1, listOf("service=${service.name}", "pod=p1") to 2)
    val objective = pick(OBJECTIVES)
    emitLine(spaced(listOf("SLO", name, objective, totalMetric, badMetric) + labels.shuffled(random)))
    sloNames += name
}

fun defineSteadySlo(service: ServiceModel) {
    val name = freshRuleName(sloNames)
    val objective = pick(listOf("99", "90", "99.5"))
    val pod = service.pods.first()
    emitLine(spaced(listOf("SLO", name, objective, pod.requests.metric, ERROR_METRICS.first(), "service=${service.name}")))
    sloNames += name
    val exactBurn = java.math.BigDecimal(service.errorRate).divide(java.math.BigDecimal(service.requestRate))
        .multiply(java.math.BigDecimal(100)).divide(java.math.BigDecimal(100).subtract(java.math.BigDecimal(objective)))
    val longWindow = step * random.nextLong(3, 9)
    val shortWindow = step * random.nextLong(2, longWindow / step)
    for (threshold in listOf(exactBurn, exactBurn.subtract(java.math.BigDecimal("0.001")))) {
        val alert = freshRuleName(alertNames)
        emitLine(spaced(listOf("ALERT", alert, name, longWindow.toString(), shortWindow.toString(), threshold.stripTrailingZeros().toPlainString())))
        alertNames += alert
    }
}

fun windowLength(): Long = weighted(
    step * random.nextLong(2, 5) to 5,
    step * random.nextLong(5, 15) to 3,
    step to 1,
    step * 2 - 1 to 1,
    random.nextLong(1, step * 20) to 2,
    MAX_TIME to 1,
)

fun defineAlert() {
    if (sloNames.isEmpty()) return defineSlo()
    val name = freshRuleName(alertNames)
    val longWindow = weighted(step * random.nextLong(3, 12) to 6, random.nextLong(2, step * 20) to 2, MAX_TIME to 1)
    val shortWindow = weighted(longWindow / 12 to 2, random.nextLong(1, longWindow) to 4, step * 2 to 2).coerceIn(1, longWindow - 1)
    val threshold = weighted(pick(THRESHOLDS) to 6, "1" to 2, "0.999" to 1, "10" to 1)
    emitLine(spaced(listOf("ALERT", name, pick(sloNames), longWindow.toString(), shortWindow.toString(), threshold)))
    alertNames += name
}

fun burn() {
    val slo = if (sloNames.isEmpty() || chance(0.05)) pick(listOf("nope", "missing")) else pick(sloNames)
    val time = weighted(now to 4, (now - step * random.nextLong(0, 10)).coerceAtLeast(0) to 3, now + random.nextLong(1, step) to 1)
    emitLine(spaced(listOf("BURN", time.toString(), windowLength().toString(), slo)))
}

fun evaluate() {
    val interval = weighted(step to 5, step * 2 to 2, step / 2 to 1, 7L to 1, 1L to 1, 45L to 1, MAX_TIME to 1).coerceAtLeast(1)
    val end = weighted(now to 4, (now - step * random.nextLong(0, 5)).coerceAtLeast(0) to 2, now + random.nextLong(0, step * 3) to 1)
    val wanted = random.nextLong(0, MAX_EVALUATION_TIMES)
    val noTimeStart = (end / interval * interval + 1).coerceAtMost(end)
    val start = weighted(
        (end - interval * wanted).coerceAtLeast(0) to 6,
        end to 1,
        (end - random.nextLong(0, step)).coerceAtLeast(0) to 1,
        0L to 1,
        noTimeStart to 1,
    )
    val clampedStart = maxOf(start, end - interval * (MAX_EVALUATION_TIMES - 1))
    emitLine(spaced(listOf("EVAL", clampedStart.toString(), end.toString(), interval.toString())))
}

fun counterQuery() {
    val service = pick(services)
    val metric = weighted(service.pods.first().requests.metric to 3, pick(ERROR_METRICS) to 2, "inflight" to 1, "latency_ms" to 1)
    val command = weighted("INCREASE" to 3, "RATE" to 2, "DELTA" to 1, "QUANTILE" to 1)
    val head = if (command == "QUANTILE") listOf(command, now.toString(), windowLength().toString(), "0.9") else listOf(command, now.toString(), windowLength().toString())
    emitLine(spaced(head + metric + "service=${service.name}"))
}

fun badLine() {
    val slo = sloNames.randomOrNull(random) ?: "checkout"
    val alert = alertNames.randomOrNull(random)
    val requests = REQUEST_METRICS.first { metric -> definedMetrics.any { it.first == metric } }
    val errors = ERROR_METRICS.first { metric -> definedMetrics.any { it.first == metric } }
    val line = weighted(
        listOf("SLO", freshRuleName(sloNames), pick(BAD_OBJECTIVES), requests, errors) to 3,
        listOf("SLO", pick(BAD_RULE_NAMES), "99", requests, errors) to 1,
        listOf("SLO", freshRuleName(sloNames), "99", requests) to 1,
        listOf("SLO", freshRuleName(sloNames), "99", requests, "missing_total") to 2,
        listOf("SLO", freshRuleName(sloNames), "99", "missing_total", pick(listOf("inflight", "latency_ms", errors))) to 1,
        listOf("SLO", freshRuleName(sloNames), "99", requests, pick(listOf("inflight", "latency_ms"))) to 2,
        listOf("SLO", freshRuleName(sloNames), "99", pick(listOf("inflight", "latency_ms")), errors) to 1,
        listOf("SLO", slo, "99", requests, "missing_total") to 2,
        listOf("SLO", slo, pick(BAD_OBJECTIVES), requests, errors) to 1,
        listOf("SLO", freshRuleName(sloNames), "99", requests, errors, "service=a", "service=b") to 1,
        listOf("SLO", freshRuleName(sloNames), "99", "9bad", errors) to 1,
        listOf("BURN", now.toString(), "60", pick(listOf("nope", "missing", "Checkout"))) to 2,
        listOf("BURN", now.toString(), pick(BAD_LENGTHS), slo) to 2,
        listOf("BURN", pick(BAD_TIMES), "60", slo) to 1,
        listOf("BURN", now.toString(), "60") to 1,
        listOf("BURN", now.toString(), "60", slo, "extra") to 1,
        listOf("BURN", now.toString(), "60", pick(BAD_RULE_NAMES)) to 1,
        listOf("ALERT", freshRuleName(alertNames), slo, "300", "300", "14.4") to 2,
        listOf("ALERT", freshRuleName(alertNames), slo, "300", "600", "14.4") to 1,
        listOf("ALERT", freshRuleName(alertNames), slo, "3600", "300", pick(BAD_THRESHOLDS)) to 3,
        listOf("ALERT", freshRuleName(alertNames), slo, pick(BAD_LENGTHS), "300", "6") to 1,
        listOf("ALERT", freshRuleName(alertNames), pick(listOf("nope", "missing")), "3600", "300", "14.4") to 2,
        listOf("ALERT", alert ?: freshRuleName(alertNames), pick(listOf("nope", if (alert == null) "missing" else slo)), "3600", "300", "14.4") to 2,
        listOf("ALERT", alert ?: freshRuleName(alertNames), slo, "300", "300", "6") to 1,
        listOf("ALERT", freshRuleName(alertNames), slo, "3600", "300") to 1,
        listOf("ALERT", pick(BAD_RULE_NAMES), slo, "3600", "300", "6") to 1,
        listOf("EVAL", now.toString(), (now - random.nextLong(1, 100)).coerceAtLeast(0).toString(), step.toString()) to 2,
        listOf("EVAL", "0", now.toString(), "0") to 1,
        listOf("EVAL", now.toString(), now.toString()) to 1,
        listOf("EVAL", pick(BAD_TIMES), now.toString(), "10") to 1,
        listOf("EVAL", "0", now.toString(), pick(BAD_LENGTHS)) to 1,
        listOf("EVAL", now.toString(), now.toString(), "10", "x") to 1,
    )
    emitLine(spaced(line))
}

fun malformedLine() {
    val line = weighted(
        "${pick(UNKNOWN_COMMANDS)} ${pick(sloNames.ifEmpty { listOf("x") })}" to 4,
        pick(UNKNOWN_COMMANDS) to 1,
        "BURN $now 60 ${sloNames.randomOrNull(random) ?: "x"}\r" to 1,
        "EVAL 0 $now\t$step" to 1,
        "\r" to 1,
    )
    emitLine(line)
}

fun setupNormal() {
    step = pick(listOf(10L, 15L, 30L, 60L))
    now = weighted(0L to 3, step * random.nextLong(1, 100) to 3, MAX_TIME - step * 2_000 to 1)
    now -= now % step
    defineMetric(REQUEST_METRICS.first(), "counter")
    defineMetric(ERROR_METRICS.first(), "counter")
    if (chance(0.5)) defineMetric(REQUEST_METRICS[1], "counter")
    if (chance(0.5)) defineMetric(ERROR_METRICS[1], "counter")
    defineMetric("inflight", "gauge")
    defineMetric("latency_ms", "histogram", listOf("100", "300"))
    val requestMetrics = REQUEST_METRICS.filter { metric -> definedMetrics.any { it.first == metric } }
    val serviceCount = random.nextInt(1, 3)
    for (name in SERVICE_NAMES.shuffled(random).take(serviceCount)) {
        services += ServiceModel(name, requestRate = pick(listOf(600L, 60L, 100L, 1_000L, 7L)), steady = false)
    }
    val steady = if (chance(0.6)) ServiceModel("steady", requestRate = 600L, steady = true).also { it.errorRate = pick(STEADY_ERROR_RATES) } else null
    if (steady != null) services += steady
    for (service in services) {
        val metric = pick(requestMetrics)
        repeat(weighted(1 to 3, 2 to 2)) { index ->
            service.pods += PodModel(service.name, "p${index + 1}").also { pod ->
                pod.requests = CounterModel(metric, "service=${service.name} pod=p${index + 1}")
            }
        }
    }
    repeat(random.nextInt(1, 3)) { defineSlo() }
    repeat(random.nextInt(0, 3)) { defineAlert() }
    if (steady != null) defineSteadySlo(steady)
}

fun runNormal() {
    setupNormal()
    while (emittedLines < options.lines) {
        if (now + step > MAX_TIME) break
        now += step
        scrape(now)
        repeat(random.nextInt(0, 3)) {
            when (weighted(
                "burn" to 8,
                "eval" to 4,
                "slo" to 1,
                "alert" to 3,
                "counter" to 2,
                "bad" to 5,
                "malformed" to 1,
                "blank" to 1,
            )) {
                "burn" -> burn()
                "eval" -> evaluate()
                "slo" -> if (sloNames.size < 6) defineSlo() else badLine()
                "alert" -> if (alertNames.size < 8) defineAlert() else badLine()
                "counter" -> counterQuery()
                "bad" -> badLine()
                "malformed" -> malformedLine()
                else -> emitLine(pick(listOf("", "  ")))
            }
        }
    }
    if (alertNames.isNotEmpty()) emitLine("EVAL ${(now - step * 150).coerceAtLeast(0)} $now $step")
}

val LARGE_STEP = 2L
val LARGE_PODS = 2
val LARGE_ZONES = listOf("east", "west")
val LARGE_SLOS = listOf(
    Triple("checkout", "99.9", "job=checkout"),
    Triple("checkout-gold", "99.95", "job=checkout"),
    Triple("checkout-east", "99.5", "job=checkout zone=east"),
    Triple("checkout-west", "99.5", "job=checkout zone=west"),
)
val LARGE_ALERTS = listOf(
    Triple("page", 3_600L to 300L, "14.4"),
    Triple("page-slow", 21_600L to 1_800L, "6"),
    Triple("ticket", 259_200L to 21_600L, "1"),
)
val EVALUATION_INTERVALS = listOf(10L, 15L, 17L, 30L)
val MAX_TIMES_PER_EVALUATION = 20_000L

class LargeCounter(val line: String) {
    var value = 0L
}

fun largeIncidents(span: Long): List<Pair<LongRange, Double>> = List(random.nextInt(5, 9)) {
    val start = random.nextLong(0, span)
    val length = weighted(random.nextLong(600, 3_600) to 3, random.nextLong(3_600, 21_600) to 2, random.nextLong(21_600, 86_400) to 1)
    val errorPercent = weighted(random.nextDouble(2.0, 20.0) to 3, random.nextDouble(0.3, 2.0) to 3, random.nextDouble(0.05, 0.3) to 2)
    start..start + length to errorPercent
}

fun runLarge() {
    val span = options.days * 86_400L
    emit("TYPE requests_total counter")
    emit("TYPE errors_total counter")
    emit("TYPE queue_depth gauge")
    val pods = (1..LARGE_PODS).map { pod -> "job=checkout zone=${LARGE_ZONES[(pod - 1) * LARGE_ZONES.size / LARGE_PODS]} pod=p$pod" }
    val requestCounters = pods.map { LargeCounter("requests_total $it") }
    val errorCounters = pods.map { LargeCounter("errors_total $it") }
    val incidents = LARGE_ZONES.associateWith { largeIncidents(span) }
    val backgroundCount = options.series - requestCounters.size - errorCounters.size
    val backgroundSamples = 5
    val background = List(backgroundCount) { index ->
        if (index % 5 == 4) LargeCounter("queue_depth queue=q$index") else LargeCounter("requests_total job=batch pod=b$index")
    }
    val backgroundTimes = LongArray(backgroundCount * backgroundSamples)
    for (index in 0 until backgroundCount) {
        val first = random.nextLong(0, span / backgroundSamples)
        for (sample in 0 until backgroundSamples) {
            backgroundTimes[index * backgroundSamples + sample] = (first + sample * (span / backgroundSamples)) * 65_536 + index
        }
    }
    backgroundTimes.sort()
    var backgroundPosition = 0

    for (time in 0..span step LARGE_STEP) {
        while (backgroundPosition < backgroundTimes.size && backgroundTimes[backgroundPosition] / 65_536 <= time) {
            val counter = background[(backgroundTimes[backgroundPosition] % 65_536).toInt()]
            counter.value += random.nextLong(0, 300)
            emit("ADD ${backgroundTimes[backgroundPosition] / 65_536} ${counter.value} ${counter.line}")
            backgroundPosition++
        }
        for (pod in pods.indices) {
            val zone = LARGE_ZONES[pod * LARGE_ZONES.size / LARGE_PODS]
            val requests = 500L + random.nextLong(0, 200)
            val errorPercent = incidents.getValue(zone).firstOrNull { time in it.first }?.second ?: 0.0
            val errors = (requests * errorPercent / 100).toLong() + if (random.nextInt(50) == 0) 1 else 0
            val restart = random.nextInt(100_000) == 0
            for ((counter, increment) in listOf(requestCounters[pod] to requests, errorCounters[pod] to errors)) {
                counter.value = if (restart) random.nextLong(0, 100) else counter.value + increment
                emit("ADD $time ${counter.value} ${counter.line}")
            }
        }
    }

    for ((name, objective, labels) in LARGE_SLOS) emit("SLO $name $objective requests_total errors_total $labels")
    for ((slo, _, _) in LARGE_SLOS) {
        for ((alert, windows, threshold) in LARGE_ALERTS) emit("ALERT $slo-$alert $slo ${windows.first} ${windows.second} $threshold")
    }
    val burnWindows = listOf(300L, 1_800L, 3_600L, 21_600L, 86_400L, 259_200L)
    repeat(options.burns) {
        val window = weighted(pick(burnWindows) to 8, random.nextLong(1, span) to 2)
        emit("BURN ${random.nextLong(0, span + 1)} $window ${pick(LARGE_SLOS).first}")
    }
    var remaining = options.evaluations.toLong()
    var position = 0
    while (remaining > 0) {
        val interval = EVALUATION_INTERVALS[position++ % EVALUATION_INTERVALS.size]
        val end = span - random.nextLong(0, 3_600) / interval * interval
        val count = minOf(remaining, MAX_TIMES_PER_EVALUATION, end / interval + 1)
        emit("EVAL ${end - (count - 1) * interval} $end $interval")
        remaining -= count
    }
}

if (options.mode == "large") runLarge() else runNormal()
output.flush()
