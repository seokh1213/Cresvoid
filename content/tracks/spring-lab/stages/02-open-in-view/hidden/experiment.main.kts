@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

fun readMetrics(client: HttpClient, base: String): Map<String, Int> {
    val response = client.send(get(base, "/metrics"), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "metrics unavailable" }
    return response.body().lineSequence().filter { it.isNotBlank() }.associate { line ->
        val fields = line.split('=', limit = 2)
        check(fields.size == 2) { "malformed metric" }
        fields[0] to fields[1].toInt()
    }
}

fun fields(body: String): Map<String, String>? {
    var quoted = false
    var escaped = false
    val compact = buildString {
        body.forEach { character ->
            if (quoted || !character.isWhitespace()) append(character)
            if (character == '"' && !escaped) quoted = !quoted
            escaped = quoted && character == '\\' && !escaped
        }
    }
    val matches = Regex(""""(id|owner|items|external)":(\d+|"[^"]*"|\["[^"]*","[^"]*"\])""")
        .findAll(compact).toList()
    if (matches.size != 4 || compact != matches.joinToString(",", "{", "}") { it.value }) return null
    return matches.associate { it.groupValues[1] to it.groupValues[2] }.takeIf { it.size == 4 }
}

fun correct(outcome: Outcome): Boolean {
    val id = outcome.arrival.request.uri().path.substringAfterLast('/').toLong()
    val expected = mapOf("id" to "$id", "owner" to "\"reader-$id\"",
        "items" to "[\"book-$id\",\"pen-$id\"]", "external" to "\"checked-$id\"")
    return outcome.status == 200 && fields(outcome.body) == expected
}

fun measure(base: String): Int {
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().use { client ->
        runLoad(client, steady(8, 1.0) { get(base, "/purchases/${it % 40 + 1}", Duration.ofSeconds(15)) })
        val reset = client.send(post(base, "/metrics/reset", ""), HttpResponse.BodyHandlers.ofString())
        check(reset.statusCode() == 200) { "reset unavailable" }
        val low = steady(10, 2.0) { get(base, "/purchases/${it % 40 + 1}", Duration.ofSeconds(15)) }
        val high = steady(60, 3.0, 3000) { get(base, "/purchases/${it % 40 + 1}", Duration.ofSeconds(15)) }
        val samples = (0..160).map { Arrival(it * 50L, get(base, "/metrics")) }
        val outcomes = runLoad(client, low + high + samples)
        val requests = outcomes.filter { it.arrival.request.uri().path.startsWith("/purchases/") }
        val metricSamples = outcomes.filter { it.arrival.request.uri().path == "/metrics" }
        check(metricSamples.all { it.status == 200 }) { "sampling failed" }
        val pendingPeak = metricSamples.maxOf { sample ->
            sample.body.lineSequence().first { it.startsWith("pending=") }.substringAfter('=').toInt()
        }
        val state = readMetrics(client, base)
        val bad = requests.count { !correct(it) }
        val lowP95 = percentile(requests.filter { it.arrival.atMillis < 3000 }.map { it.elapsedMillis }, 0.95)
        val highP95 = percentile(requests.filter { it.arrival.atMillis >= 3000 }.map { it.elapsedMillis }, 0.95)
        metric("requests", requests.size)
        metric("bad_responses", bad)
        metric("external_calls", state.getValue("external_calls"))
        metric("held_calls", state.getValue("held_calls"))
        metric("pool_size", state.getValue("pool_size"))
        metric("active_after", state.getValue("active"))
        metric("pending_after", state.getValue("pending"))
        metric("pending_peak", pendingPeak)
        metric("low_p95_ms", lowP95)
        metric("high_p95_ms", highP95)
        return if (requests.size == 200 && bad == 0 && state.getValue("external_calls") == 200 &&
            state.getValue("held_calls") == 0 && state.getValue("pool_size") == 10 &&
            state.getValue("active") == 0 && state.getValue("pending") == 0 &&
            lowP95 <= 1500 && highP95 <= 1500) 0 else 1
    }
}

val result = try {
    require(args.size == 2 && args[1] == "steady-arrivals") { "expected program directory and scenario" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile) { "build the project first" }
    requireDocker()
    val stack = ComposeStack(File("compose.yaml"), "spring-osiv", programEnvironment(program))
    try {
        stack.up(90)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        measure(base)
    } finally {
        stack.close()
    }
} catch (failure: Exception) {
    System.err.println("experiment error: ${failure.message}")
    2
}
exitProcess(result)
