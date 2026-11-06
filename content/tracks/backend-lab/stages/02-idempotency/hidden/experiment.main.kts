@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.system.exitProcess

data class PaymentCase(val key: String, val orderId: String, val amount: Int) {
    fun body() = """{"orderId":"$orderId","amount":$amount}"""
    fun reordered() = """{ "amount" : $amount, "orderId" : "$orderId" }"""
    fun amountConflict() = """{"orderId":"$orderId","amount":${amount + 100}}"""
    fun orderConflict() = """{"orderId":"other-$orderId","amount":$amount}"""
}

data class Measurements(
    val gatewayCalls: Int,
    val callViolations: Int,
    val responseMismatches: Int,
    val unexpectedResponses: Int,
    val conflictFailures: Int,
    val transportErrors: Int,
) {
    fun passed() = callViolations == 0 && responseMismatches == 0 && unexpectedResponses == 0 &&
        conflictFailures == 0 && transportErrors == 0
}

data class Probe(val outcomes: List<Outcome>, val controls: List<Outcome>, val failures: Int)
data class KeyResults(val case: PaymentCase, val originals: List<Outcome>, val conflicts: List<Outcome>)

fun held(outcome: Outcome) = outcome.status == 200 && outcome.body == """{"held":true}"""

fun probe(client: HttpClient, base: String, case: PaymentCase): Probe {
    val controls = mutableListOf<Outcome>()
    val outcomes = mutableListOf<Outcome>()
    var failures = 0
    Executors.newVirtualThreadPerTaskExecutor().use { executor ->
        val original = executor.submit<List<Outcome>> { runLoad(client, listOf(Arrival(0, payment(base, case, case.body())))) }
        try {
            val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
            do {
                val state = runLoad(client, listOf(Arrival(0, get(base, "/gate", Duration.ofSeconds(15))))).single()
                controls += state
                if (held(state)) break
                Thread.sleep(10)
            } while (!original.isDone && System.nanoTime() < deadline)
            if (!held(controls.last())) failures++
            outcomes += runLoad(client, listOf(case.amountConflict(), case.orderConflict()).map {
                Arrival(0, payment(base, case, it))
            })
            if (original.isDone) failures++
            val state = runLoad(client, listOf(Arrival(0, get(base, "/gate", Duration.ofSeconds(15))))).single()
            controls += state
            if (!held(state)) failures++
        } finally {
            val released = runLoad(client, listOf(Arrival(0, post(base, "/gate/release", "", Duration.ofSeconds(15))))).single()
            controls += released
            if (released.status != 200 || released.body != """{"released":true}""") failures++
        }
        outcomes += original.get()
        outcomes += runLoad(client, listOf(Arrival(0, payment(base, case, case.body()))))
    }
    return Probe(outcomes, controls, failures)
}

fun payment(base: String, case: PaymentCase, body: String) = post(
    base, "/payments", body, Duration.ofSeconds(15),
    mapOf("Idempotency-Key" to case.key, "Content-Type" to "application/json"),
)

fun schedule(base: String, cases: List<PaymentCase>, phase: Int): List<Arrival> = cases.flatMapIndexed { index, case ->
    val bodies = if (phase == 0) List(3) { case.body() }
    else listOf(case.body(), case.reordered(), case.body(), case.amountConflict(), case.orderConflict())
    bodies.map { Arrival(index * 100L, payment(base, case, it)) }
}

fun validResponse(case: PaymentCase, outcome: Outcome): Boolean {
    if (case.amount % 7 == 0) {
        return outcome.status == 504 &&
            Regex("""\{"error":"gateway_timeout","attemptId":"a-[1-9][0-9]*"}""").matches(outcome.body)
    }
    val shape = Regex("""\{"paymentId":"p-[1-9][0-9]*","orderId":"${case.orderId}","amount":${case.amount}}""")
    return outcome.status == 201 && shape.matches(outcome.body)
}

fun measure(results: List<KeyResults>, stats: List<Outcome>): Measurements {
    val statsPattern = Regex("""\{"calls":([0-9]+)}""")
    val calls = stats.map { outcome ->
        if (outcome.status == 200) statsPattern.matchEntire(outcome.body)?.groupValues?.get(1)?.toIntOrNull() else null
    }
    var mismatches = 0
    var unexpected = calls.count { it == null }
    var conflicts = 0
    val identifiers = mutableSetOf<String>()
    results.forEach { (case, originals, conflictResponses) ->
        if (originals.map { it.status to it.body }.distinct().size != 1) mismatches++
        unexpected += originals.count { !validResponse(case, it) }
        val id = Regex(""""(?:paymentId|attemptId)":"([^"]+)"""").find(originals.first().body)?.groupValues?.get(1)
        if (id != null && !identifiers.add(id)) unexpected++
        conflicts += conflictResponses.count {
            it.status != 422 || it.body != """{"error":"key_conflict"}"""
        }
    }
    return Measurements(
        gatewayCalls = calls.filterNotNull().sum(),
        callViolations = calls.count { it != 1 },
        responseMismatches = mismatches,
        unexpectedResponses = unexpected,
        conflictFailures = conflicts,
        transportErrors = (results.flatMap { it.originals + it.conflicts } + stats).count { it.error != null },
    )
}

fun experiment(program: File, scenario: String): Int {
    val count = when (scenario) {
        "parallel-arrivals" -> 14
        "later-arrivals" -> 21
        else -> error("Unknown scenario")
    }
    val baseAmount = if (count == 14) 1400 else 2100
    val cases = List(count) { index ->
        val requestIndex = if (index == 1) 0 else index
        PaymentCase("batch-$index", "order-$requestIndex", baseAmount + requestIndex)
    }
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    val environment = mapOf(
        "PORT" to port.toString(),
        "PATH" to "${System.getProperty("java.home")}/bin:${System.getenv("PATH")}",
        "LAB_HOLD_KEY" to "probe",
    )
    val server = Program(program, environment, base)
    try {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
            val probeCase = PaymentCase("probe", "probe-order", 1000)
            val probe = probe(client, base, probeCase)
            val first = runLoad(client, schedule(base, cases, 0))
            val later = runLoad(client, schedule(base, cases, 1))
            val allCases = cases + probeCase
            val payments = first + later + probe.outcomes
            val stats = runLoad(client, allCases.map { Arrival(0, get(base, "/stats?key=${it.key}", Duration.ofSeconds(15))) })
            val results = cases.mapIndexed { index, case ->
                KeyResults(case, first.subList(index * 3, index * 3 + 3) + later.subList(index * 5, index * 5 + 3),
                    later.subList(index * 5 + 3, index * 5 + 5))
            } + KeyResults(probeCase, probe.outcomes.takeLast(2), probe.outcomes.take(2))
            val measurements = measure(results, stats)
            metric("key_count", allCases.size)
            metric("gateway_calls", measurements.gatewayCalls)
            metric("call_violations", measurements.callViolations)
            metric("response_mismatches", measurements.responseMismatches)
            metric("unexpected_responses", measurements.unexpectedResponses)
            metric("conflict_failures", measurements.conflictFailures)
            val transportErrors = measurements.transportErrors + probe.controls.count { it.error != null }
            metric("transport_errors", transportErrors)
            metric("inflight_failures", probe.failures)
            metric("p99_ms", percentile(payments.map { it.elapsedMillis }, 0.99))
            return if (measurements.passed() && probe.failures == 0 && transportErrors == 0) 0 else 1
        }
    } finally {
        server.close()
    }
}

val exit = try {
    require(args.size == 2 && File(args[0]).isAbsolute) { "Expected absolute program path and scenario" }
    experiment(File(args[0]), args[1])
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(exit)
