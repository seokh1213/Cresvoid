@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class Window(val page: Int, val size: Int)

fun expectedBody(window: Window, total: Int): String = buildString {
    append("TOTAL\t$total\n")
    val first = window.page * window.size + 1
    val last = minOf((window.page + 1) * window.size, total)
    for (id in first..last) {
        val state = if (id % 3 == 0) "CLOSED" else "OPEN"
        val items = (1..(id % 5)).joinToString(",") { (id * 10 + it).toString() }
        append("$id\t$state\t$items\n")
    }
}

fun samePage(body: String, expected: String): Boolean = body == expected

fun readMetrics(client: HttpClient, base: String): Map<String, Long> {
    val response = client.send(get(base, "/metrics"), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "metrics endpoint failed" }
    return response.body().lineSequence().filter { it.isNotBlank() }.associate { line ->
        val fields = line.split('=')
        check(fields.size == 2) { "invalid service metric" }
        fields[0] to fields[1].toLong()
    }.also { values ->
        check(values.keys == setOf("requests", "sql_total", "sql_max", "sql_min", "entities_max", "overfetches", "paging_warnings"))
        check(values.values.all { it >= 0 })
    }
}

fun observe(client: HttpClient, base: String, scenario: String): Boolean {
    val warmWindow = Window(0, 20)
    val warm = client.send(get(base, "/orders?page=0&size=20", Duration.ofSeconds(15)), HttpResponse.BodyHandlers.ofString())
    var contentErrors = if (samePage(warm.body(), expectedBody(warmWindow, 137))) 0 else 1
    var responseErrors = if (warm.statusCode() == 200) 0 else 1
    val total = if (scenario == "catalog-refresh") 139 else 137
    if (scenario == "catalog-refresh") {
        for (id in 138..139) {
            val added = client.send(post(base, "/admin/append", ""), HttpResponse.BodyHandlers.ofString())
            if (added.statusCode() != 200) responseErrors++
            if (added.body() != "$id\n") contentErrors++
        }
    }
    val windows = listOf(Window(0, 1), Window(1, 7), Window(3, 20), Window(0, 40),
        Window(3, 40), Window(6, 20), Window(19, 7), Window(137, 1), Window(200, 7), Window(0, 20))
    val arrivals = steady(12, 4.0) { index ->
        val window = windows[index % windows.size]
        get(base, "/orders?page=${window.page}&size=${window.size}", Duration.ofSeconds(15))
    }
    val outcomes = runLoad(client, arrivals)
    for ((index, outcome) in outcomes.withIndex()) {
        if (outcome.status != 200) responseErrors++
        if (!samePage(outcome.body, expectedBody(windows[index % windows.size], total))) contentErrors++
    }
    val stats = readMetrics(client, base)
    val requests = stats.getValue("requests")
    val sqlTotal = stats.getValue("sql_total")
    val sqlMax = stats.getValue("sql_max")
    val sqlMin = stats.getValue("sql_min")
    val warnings = stats.getValue("paging_warnings")
    val overfetches = stats.getValue("overfetches")
    if (requests != outcomes.size + 1L) responseErrors++
    metric("requests", requests)
    metric("response_errors", responseErrors)
    metric("content_errors", contentErrors)
    metric("sql_per_request_max", sqlMax)
    metric("sql_per_request_min", sqlMin)
    metric("sql_per_request_mean", sqlTotal.toDouble() / maxOf(1L, requests))
    metric("paging_warnings", warnings)
    metric("overfetches", overfetches)
    metric("entities_per_request_max", stats.getValue("entities_max"))
    metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
    return responseErrors == 0 && contentErrors == 0 && sqlMax in 1L..3L && sqlMin >= 1L &&
        sqlTotal >= requests && warnings == 0L && overfetches == 0L
}

fun experiment(): Int {
    require(args.size == 2 && File(args[0]).isAbsolute) { "expected absolute program directory and scenario" }
    val scenario = args[1]
    require(scenario in setOf("catalog-browse", "catalog-refresh")) { "unknown scenario" }
    requireDocker()
    val port = freePort()
    val stack = ComposeStack(File("compose.yaml"), "spring-n-plus-one",
        programEnvironment(File(args[0])) + mapOf("APP_PORT" to port.toString()))
    try {
        stack.up(150)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
            val ready = client.send(get(base, "/health"), HttpResponse.BodyHandlers.ofString())
            check(ready.statusCode() == 200 && ready.body() == "ready\n")
            return if (observe(client, base, scenario)) 0 else 1
        }
    } finally {
        stack.close()
    }
}

val result = try {
    experiment()
} catch (error: Exception) {
    System.err.println("experiment error: ${error.message}")
    2
}
exitProcess(result)
