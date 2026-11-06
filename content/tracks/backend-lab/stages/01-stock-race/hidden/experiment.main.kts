@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration
import java.util.Locale
import kotlin.math.abs
import kotlin.system.exitProcess

fun compactJson(body: String): String = buildString {
    var inString = false
    var escaped = false
    for (char in body) {
        if (inString || char !in " \t\r\n") append(char)
        if (escaped) escaped = false
        else if (inString && char == '\\') escaped = true
        else if (char == '"') inString = !inString
    }
}

fun remaining(outcome: Outcome, productId: String): Int? {
    if (outcome.status != 200) return null
    val compact = compactJson(outcome.body)
    val forward = Regex("""\{"productId":"$productId","remaining":(-?(?:0|[1-9][0-9]*))}""")
    val reverse = Regex("""\{"remaining":(-?(?:0|[1-9][0-9]*)),"productId":"$productId"}""")
    return (forward.matchEntire(compact) ?: reverse.matchEntire(compact))
        ?.groupValues?.get(1)?.toIntOrNull()
}

fun orderBody(productId: String, quantity: Int): String =
    """{"productId":"$productId","quantity":$quantity}"""

fun observe(client: HttpClient, request: HttpRequest): Outcome =
    runLoad(client, listOf(Arrival(0, request))).single()

data class ContractProbe(val request: HttpRequest, val status: Int, val body: String)

fun matchesContract(probe: ContractProbe, outcome: Outcome): Boolean {
    if (outcome.status != probe.status) return false
    val expectedRemaining = Regex(""""remaining":([0-9]+)""").find(probe.body)
    return if (expectedRemaining != null) {
        val productId = Regex(""""productId":"([a-zA-Z0-9-]+)""").find(probe.body)!!.groupValues[1]
        remaining(outcome, productId) == expectedRemaining.groupValues[1].toInt()
    } else compactJson(outcome.body) == probe.body
}

data class RunningService(val base: String, val program: Program)

fun startService(programDir: File): RunningService {
    repeat(3) { attempt ->
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        try {
            return RunningService(base, Program(programDir, mapOf("PORT" to port.toString()), base))
        } catch (error: IllegalStateException) {
            // The free-port probe releases its socket before the server binds.
            if (attempt == 2) throw error
        }
    }
    error("서비스를 시작하지 못했다")
}

fun contractProbes(base: String): List<ContractProbe> = listOf(
    ContractProbe(get(base, "/health"), 200, """{"status":"ready"}"""),
    ContractProbe(get(base, "/stock/contract"), 200, """{"productId":"contract","remaining":100}"""),
    ContractProbe(get(base, "/stock/missing"), 404, """{"error":"not-found"}"""),
    ContractProbe(post(base, "/orders", """{"productId":"contr\u0061ct","quantity":101}"""), 409, """{"error":"insufficient-stock"}"""),
    ContractProbe(post(base, "/orders", """{"\u0070roductId":"contract","quantity":101}"""), 409, """{"error":"insufficient-stock"}"""),
    ContractProbe(post(base, "/orders", "\u000c" + orderBody("contract", 1)), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", "\u000b" + orderBody("contract", 1)), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", " \t\r\n" + orderBody("contract", 101)), 409, """{"error":"insufficient-stock"}"""),
    ContractProbe(post(base, "/orders", orderBody("contract", 101).padEnd(4096)), 409, """{"error":"insufficient-stock"}"""),
    ContractProbe(post(base, "/orders", orderBody("contract", 101).padEnd(4097)), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", """{"productId":"contract","quantity":10001}"""), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", """{"productId":"contract","quantity":1,"extra":1}"""), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", """{"productId":"contract"}"""), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", """{"productId":"contract","quantity":1,}"""), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", orderBody("contract", 101)), 409, """{"error":"insufficient-stock"}"""),
    ContractProbe(get(base, "/stock/contract"), 200, """{"productId":"contract","remaining":100}"""),
    ContractProbe(post(base, "/orders", orderBody("contract", 3)), 201, """{"status":"accepted"}"""),
    ContractProbe(get(base, "/stock/contract"), 200, """{"productId":"contract","remaining":97}"""),
    ContractProbe(post(base, "/orders", """{"quantity":97,"productId":"contract"}"""), 201, """{"status":"accepted"}"""),
    ContractProbe(post(base, "/orders", orderBody("contract", 1)), 409, """{"error":"insufficient-stock"}"""),
    ContractProbe(post(base, "/orders", orderBody("contract", 0)), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", orderBody("contract", -1)), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", "{}"), 400, """{"error":"invalid-order"}"""),
    ContractProbe(post(base, "/orders", orderBody("missing", 1)), 404, """{"error":"not-found"}"""),
    ContractProbe(get(base, "/stock/contract"), 200, """{"productId":"contract","remaining":0}"""),
    ContractProbe(get(base, "/stock/sku-1"), 200, """{"productId":"sku-1","remaining":100}""")
)

fun experiment(programDir: File): Int {
    val service = startService(programDir)
    val base = service.base
    service.program.use {
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build().use { client ->
                val probes = contractProbes(base)
                val probeOutcomes = probes.map { observe(client, it.request) }
                val contractErrors = probes.zip(probeOutcomes).count { (probe, outcome) -> !matchesContract(probe, outcome) }
                val timeout = Duration.ofSeconds(30)
                val warmupRequest = post(base, "/orders", orderBody("warmup", 1), timeout)
                val measuredRequest = post(base, "/orders", orderBody("sku-1", 1), timeout)
                val arrivals = steady(400, 5.0) { warmupRequest } +
                    steady(400, 20.0, 5_000) { measuredRequest }
                val outcomes = runLoad(client, arrivals)
                val measured = outcomes.filter { it.arrival.atMillis >= 5_000 }
                val finalStock = observe(client, get(base, "/stock/sku-1", timeout))
                val finalRemaining = remaining(finalStock, "sku-1")
                val all = probeOutcomes + outcomes + finalStock
                all.filter { it.error != null || it.status == 0 }.forEach {
                    System.err.println("전송 오류: path=${it.arrival.request.uri().path} at_ms=${it.arrival.atMillis} elapsed_ms=${it.elapsedMillis} error=${it.error}")
                }
                val accepted = measured.count { it.status == 201 }
                val rejected = measured.count { it.status == 409 }
                val serverErrors = all.count { it.status in 500..599 }
                val transportErrors = all.count { it.error != null || it.status == 0 }
                val unexpected = outcomes.count {
                    when (it.status) {
                        201 -> compactJson(it.body) != """{"status":"accepted"}"""
                        409 -> compactJson(it.body) != """{"error":"insufficient-stock"}"""
                        else -> true
                    }
                }
                val observedRemaining = finalRemaining ?: -1
                val gap = abs(accepted.toLong() - (100L - observedRemaining))
                val contractFailures = contractErrors + if (finalStock.status == 200 && finalRemaining != null) 0 else 1
                metric("accepted_orders", accepted)
                metric("remaining_stock", observedRemaining)
                metric("conservation_gap", gap)
                metric("server_errors", serverErrors)
                metric("transport_errors", transportErrors)
                metric("unexpected_responses", unexpected)
                metric("scheduled_orders", 8_000)
                metric("completed_orders", measured.count { it.status > 0 })
                metric("rejected_orders", rejected)
                metric("contract_errors", contractFailures)
                return if (
                    measured.size == 8_000 && accepted == 100 && rejected == 7_900 &&
                    gap == 0L && observedRemaining == 0 && serverErrors == 0 &&
                    transportErrors == 0 && unexpected == 0 && contractFailures == 0
                ) 0 else 1
        }
    }
}

Locale.setDefault(Locale.US)
val result = try {
    require(args.size == 2) { "프로그램 경로와 시나리오 이름이 필요하다" }
    require(args[1] == "order-window") { "알 수 없는 시나리오다" }
    val programDir = File(args[0])
    require(programDir.isAbsolute && programDir.isDirectory) { "프로그램 절대 경로가 필요하다" }
    experiment(programDir)
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
