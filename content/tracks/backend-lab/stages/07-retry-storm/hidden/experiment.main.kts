@file:Import("../../../../../lab-env/load.main.kts")

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

data class Case(val id: String, val group: String, val failures: Int, val missing: Boolean, val body: String)
data class Call(val id: String, val attempt: Int, val backoff: Long, val began: Long, @Volatile var replied: Long = 0)

class Diagnostics(private val enabled: Boolean) {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val examples = ConcurrentHashMap<String, String>()

    fun record(kind: String, observed: Any, expected: String, count: Int = 1) {
        if (!enabled || count == 0) return
        counts.computeIfAbsent(kind) { AtomicInteger() }.addAndGet(count)
        examples.putIfAbsent(kind, "관측=$observed, 기대=$expected")
    }

    fun print() {
        if (!enabled) return
        if (counts.isEmpty()) System.err.println("진단: 모든 불변식을 통과했다.")
        counts.toSortedMap().forEach { (kind, count) ->
            System.err.println("진단 $kind: ${count.get()}건, ${examples.getValue(kind)}")
        }
    }
}

fun cases(group: String, count: Int): List<Case> = List(count) { index ->
    val id = UUID.randomUUID().toString()
    val missing = group != "fault" && index % 20 == 9
    val failures = if (group != "fault" && index % 20 == 19) 2 else 0
    val body = if (missing) "{\"error\":\"missing\"}" else "{\"id\":\"$id\",\"quote\":\"${UUID.randomUUID()}\"}"
    Case(id, group, failures, missing, body)
}

class Dependency(private val scenario: String, private val cases: Map<String, Case>, private val diagnostics: Diagnostics) : AutoCloseable {
    val calls = CopyOnWriteArrayList<Call>()
    val errors = AtomicInteger()
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val base = "http://127.0.0.1:${server.address.port}"
    private val cleanup = Thread { close() }

    init {
        server.executor = executor
        server.createContext("/") { exchange -> handle(exchange) }
        Runtime.getRuntime().addShutdownHook(cleanup)
        server.start()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val id = it.requestURI.path.removePrefix("/quote/")
            val case = cases[id]
            if (it.requestMethod != "GET" || case == null || it.requestURI.rawQuery != null) {
                errors.incrementAndGet()
                diagnostics.record("하위_경로_메서드", "method=${it.requestMethod}, 등록된_경로=${case != null}, query=${it.requestURI.rawQuery != null}", "GET /quote/{등록된 id}, query 없음")
                it.sendResponseHeaders(400, -1)
                return
            }
            val ordinal = counts.computeIfAbsent(id) { AtomicInteger() }.getAndIncrement()
            val attempt = it.requestHeaders.getFirst("X-Attempt")?.toIntOrNull() ?: -1
            val backoff = it.requestHeaders.getFirst("X-Backoff-Ms")?.toLongOrNull() ?: -1
            if (attempt != ordinal || (ordinal == 0 && backoff != 0L)) errors.incrementAndGet()
            if (attempt != ordinal) diagnostics.record("시도_번호", attempt, "$ordinal")
            if (ordinal == 0 && backoff != 0L) diagnostics.record("최초_대기_헤더", backoff, "0ms")
            val call = Call(id, attempt, backoff, System.nanoTime())
            calls += call
            val failed = case.group == "fault" || ordinal < case.failures
            if (case.group == "fault" && scenario == "afternoon-traffic") Thread.sleep(3000)
            else Thread.sleep(20)
            val status = when {
                failed -> 503
                case.missing -> 404
                else -> 200
            }
            val body = if (failed) "{\"error\":\"busy\"}" else case.body
            val bytes = body.toByteArray(Charsets.UTF_8)
            call.replied = System.nanoTime()
            try {
                it.sendResponseHeaders(status, bytes.size.toLong())
                it.responseBody.write(bytes)
            } catch (_: java.io.IOException) {
                // A timed-out caller may close while this dependency still works.
            }
        }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
        runCatching { Runtime.getRuntime().removeShutdownHook(cleanup) }
    }
}

data class Measurements(val normalRatio: Double, val faultP95: Double, val recoveryP95: Double, val recoveryLimit: Double)

fun latencies(outcomes: List<Outcome>, cases: Map<String, Case>): Measurements {
    fun group(name: String) = outcomes.filter { cases.getValue(it.arrival.request.uri().path.substringAfterLast('/')).group == name }
    fun valid(outcome: Outcome): Boolean {
        val case = cases.getValue(outcome.arrival.request.uri().path.substringAfterLast('/'))
        return outcome.error == null && outcome.status == (if (case.missing) 404 else 200) && outcome.body == case.body
    }
    val before = group("before")
    val after = group("after")
    val normalRatio = minOf(before.count(::valid).toDouble() / before.size, after.count(::valid).toDouble() / after.size)
    val baseline = percentile(before.map { it.elapsedMillis }, 0.95)
    return Measurements(normalRatio, percentile(group("fault").map { it.elapsedMillis }, 0.95),
        percentile(after.map { it.elapsedMillis }, 0.95), (baseline * 4).coerceIn(400.0, 1200.0))
}

fun backoffErrors(calls: List<Call>, diagnostics: Diagnostics): Int = calls.groupBy { it.id }.values.sumOf { attempts ->
    attempts.sortedBy { it.began }.zipWithNext().count { (previous, next) ->
        val ceiling = when (next.attempt) { 1 -> 80L; 2 -> 160L; else -> 0L }
        val completedFailure = previous.replied in 1..next.began
        val since = if (completedFailure) previous.replied else previous.began
        val gap = (next.began - since) / 1_000_000.0
        val invalidHeader = next.backoff !in ceiling / 2..ceiling
        val invalidGap = gap < next.backoff - 15 || (completedFailure && gap > next.backoff + 1000)
        if (invalidHeader) diagnostics.record("재시도_헤더_범위", next.backoff, "${ceiling / 2}..${ceiling}ms")
        if (invalidGap) diagnostics.record("실제_대기_간격", "${gap}ms", if (completedFailure) "${next.backoff - 15}..${next.backoff + 1000}ms" else "${next.backoff - 15}ms 이상")
        invalidHeader || invalidGap
    }
}

fun report(outcomes: List<Outcome>, dependency: Dependency, cases: Map<String, Case>, diagnostics: Diagnostics): Boolean {
    val calls = dependency.calls.toList()
    val groups = calls.groupBy { it.id }
    val faultUpper = cases.values.count { it.group == "fault" }
    val faultLower = calls.count { cases.getValue(it.id).group == "fault" }
    val measurements = latencies(outcomes, cases)
    val maxAttempts = groups.values.maxOfOrNull { it.size } ?: 0
    val missingInitial = cases.keys.count { groups[it].isNullOrEmpty() }
    val missingRetried = cases.values.count { it.missing && groups[it.id].orEmpty().size != 1 }
    val invalidFault = outcomes.count {
        val case = cases.getValue(it.arrival.request.uri().path.substringAfterLast('/'))
        case.group == "fault" && (it.status != 503 || it.body != "{\"error\":\"unavailable\"}")
    }
    val protocolErrors = dependency.errors.get() + missingInitial + missingRetried + invalidFault
    diagnostics.record("최초_호출_누락", "하위 호출 0회", "1회 이상", missingInitial)
    cases.values.filter { it.missing && groups[it.id].orEmpty().size != 1 }.forEach {
        diagnostics.record("404_호출_횟수", groups[it.id].orEmpty().size, "1회")
    }
    outcomes.filter {
        cases.getValue(it.arrival.request.uri().path.substringAfterLast('/')).group == "fault" &&
            (it.status != 503 || it.body != "{\"error\":\"unavailable\"}")
    }.forEach { diagnostics.record("장애_응답_계약", "status=${it.status}, 본문_일치=${it.body == "{\"error\":\"unavailable\"}"}", "status=503, 본문 일치") }
    val backoffErrors = backoffErrors(calls, diagnostics)
    val jitterValues = calls.filter { it.attempt == 1 && cases.getValue(it.id).group != "fault" }.map { it.backoff }.distinct().size
    metric("fault_upper_requests", faultUpper)
    metric("fault_lower_requests", faultLower)
    metric("normal_success_ratio", measurements.normalRatio)
    metric("fault_p95_ms", measurements.faultP95)
    metric("recovery_p95_ms", measurements.recoveryP95)
    metric("recovery_limit_ms", measurements.recoveryLimit)
    metric("max_attempts", maxAttempts)
    metric("protocol_errors", protocolErrors)
    metric("backoff_errors", backoffErrors)
    metric("jitter_values", jitterValues)
    if (faultLower > faultUpper * 1.2) diagnostics.record("장애_호출_총량", faultLower, "${(faultUpper * 1.2).toInt()}회 이하")
    if (measurements.normalRatio < 0.98) diagnostics.record("정상_회복_완료율", measurements.normalRatio, "0.98 이상")
    if (measurements.faultP95 > 1800) diagnostics.record("장애_p95", measurements.faultP95, "1800ms 이하")
    if (measurements.recoveryP95 > measurements.recoveryLimit) diagnostics.record("회복_p95", measurements.recoveryP95, "${measurements.recoveryLimit}ms 이하")
    if (maxAttempts > 3) diagnostics.record("최대_시도", maxAttempts, "3회 이하")
    if (jitterValues < 3) diagnostics.record("지터_표본", jitterValues, "3종 이상")
    diagnostics.print()
    return faultLower <= faultUpper * 1.2 && measurements.normalRatio >= 0.98 &&
        measurements.faultP95 <= 1800 && measurements.recoveryP95 <= measurements.recoveryLimit &&
        maxAttempts <= 3 && protocolErrors == 0 && backoffErrors == 0 && jitterValues >= 3
}

fun experiment(): Boolean {
    require(args.size in 2..3 && (args.size == 2 || args[2] == "--diagnose"))
    val diagnostics = Diagnostics(args.size == 3)
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    val scenario = args[1]
    require(scenario in setOf("morning-traffic", "afternoon-traffic"))
    val before = cases("before", 60)
    val fault = cases("fault", 160)
    val after = cases("after", 60)
    val cases = (before + fault + after).associateBy { it.id }
    Dependency(scenario, cases, diagnostics).use { dependency ->
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build().use { client ->
            Program(program, mapOf("PORT" to "$port", "DOWNSTREAM_URL" to dependency.base), base).use {
                fun schedule(items: List<Case>, rate: Int, start: Long) =
                    steady(rate, items.size.toDouble() / rate, start) { index ->
                        get(base, "/quotes/${items[index].id}", Duration.ofSeconds(6))
                    }
                val arrivals = schedule(before, 30, 0) + schedule(fault, 40, 2500) + schedule(after, 30, 6500)
                val outcomes = runLoad(client, arrivals)
                return report(outcomes, dependency, cases, diagnostics)
            }
        }
    }
}

val result = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
