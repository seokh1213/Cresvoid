@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import kotlin.system.exitProcess

fun field(json: String, name: String): Long =
    Regex("\"${Regex.escape(name)}\":(-?\\d+)").find(json)?.groupValues?.get(1)?.toLong()
        ?: error("통계 필드가 없다: $name")

fun control(client: HttpClient, request: HttpRequest, expected: Int = 200): String {
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == expected) { "제어 요청 실패: ${request.uri()} (${response.statusCode()})" }
    return response.body()
}

fun product(id: Int): String = "{\"id\":$id,\"price\":${1000 + id}}"

fun wrongResponse(outcome: Outcome): Boolean {
    val id = outcome.arrival.request.uri().path.substringAfterLast('/').toInt()
    val expectedStatus = if (id <= 10_000) 200 else 404
    val expectedBody = if (expectedStatus == 200) product(id) else "{\"error\":\"not_found\"}"
    return outcome.error != null || outcome.status != expectedStatus || outcome.body != expectedBody
}

fun mixedCatalogue(client: HttpClient, base: String): Boolean {
    val arrivals = steady(300, 6.0) { index ->
        val id = if (index % 10 < 3) 10_001 + (index / 10 * 3 + index % 10) % 6 else 1 + index % 100
        get(base, "/products/$id", Duration.ofSeconds(30))
    }
    val outcomes = runLoad(client, arrivals)
    val beforeCreation = control(client, get(base, "/admin/stats"))
    val create = HttpRequest.newBuilder(URI.create("$base/admin/products/10001"))
        .timeout(Duration.ofSeconds(10)).PUT(HttpRequest.BodyPublishers.noBody()).build()
    control(client, create, 204)
    val visible = runLoad(client, listOf(Arrival(2_500, get(base, "/products/10001", Duration.ofSeconds(30))))).single()
    val stats = control(client, get(base, "/admin/stats"))
    val visibilityErrors = if (visible.status == 200 && visible.body == product(10_001) && visible.error == null) 0 else 1
    val responseErrors = outcomes.count(::wrongResponse)
    val serverErrors = outcomes.count { it.status in 500..599 } + if (visible.status in 500..599) 1 else 0
    val missingCalls = field(beforeCreation, "missingCalls")
    val peak = field(stats, "peakSecond")
    val active = field(stats, "peakActive")
    metric("requests", outcomes.size + 1)
    metric("missing_origin_calls", missingCalls)
    metric("missing_limit", 24)
    metric("origin_peak_1s", peak)
    metric("origin_peak_active", active)
    metric("server_errors", serverErrors)
    metric("response_errors", responseErrors)
    metric("late_visibility_errors", visibilityErrors)
    return outcomes.size == 1_800 && missingCalls in 1..24 && peak <= 100 && active <= 12 &&
        serverErrors == 0 && responseErrors == 0 && visibilityErrors == 0
}

fun catalogueSweep(client: HttpClient, base: String): Boolean {
    val seeded = control(client, post(base, "/admin/seed", "", Duration.ofSeconds(30)))
    val arrivals = steady(500, 12.0) { index ->
        val id = 1 + (index * 73) % 10_000
        get(base, "/products/$id", Duration.ofSeconds(30))
    }
    val outcomes = runLoad(client, arrivals)
    val stats = control(client, get(base, "/admin/stats"))
    val responseErrors = outcomes.count(::wrongResponse)
    val serverErrors = outcomes.count { it.status in 500..599 }
    val calls = field(stats, "calls")
    val peak = field(stats, "peakSecond")
    val active = field(stats, "peakActive")
    val span = field(seeded, "span")
    val bins = field(seeded, "bins")
    val invalid = field(seeded, "invalid")
    metric("requests", outcomes.size)
    metric("origin_calls", calls)
    metric("origin_peak_1s", peak)
    metric("origin_peak_active", active)
    metric("server_errors", serverErrors)
    metric("response_errors", responseErrors)
    metric("ttl_span_ms", span)
    metric("ttl_bins", bins)
    metric("ttl_invalid", invalid)
    return outcomes.size == 6_000 && calls >= 500 && peak <= 100 && active <= 12 &&
        serverErrors == 0 && responseErrors == 0 && span >= 8_000 && bins >= 8 && invalid == 0L
}

fun experiment(): Boolean {
    require(args.size == 2) { "프로그램 절대 경로와 시나리오 이름이 필요하다" }
    val directory = File(args[0])
    require(directory.isAbsolute && File(directory, "run.sh").canExecute())
    require(args[1] in setOf("mixed-catalogue", "catalogue-sweep"))
    requireDocker()
    return ComposeStack(File("compose.yaml"), "backend-04").use { stack ->
        stack.up()
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        val environment = mapOf("PORT" to port.toString(), "REDIS_PORT" to stack.port("redis", 6379).toString())
        Program(directory, environment, base).use {
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build().use { client ->
                    when (args[1]) {
                        "mixed-catalogue" -> mixedCatalogue(client, base)
                        else -> catalogueSweep(client, base)
                    }
                }
        }
    }
}

Locale.setDefault(Locale.ROOT)
val exitCode = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(exitCode)
