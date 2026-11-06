@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun response(client: HttpClient, request: HttpRequest): String {
    val result = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(result.statusCode() == 200) { "control endpoint failed: ${result.statusCode()}" }
    return result.body()
}

fun awaitHost(client: HttpClient, base: String) {
    val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
    while (System.nanoTime() < deadline) {
        val ready = try {
            response(client, get(base, "/health", Duration.ofSeconds(1))) == "ready"
        } catch (_: java.io.IOException) {
            false
        }
        if (ready) return
        Thread.sleep(50)
    }
    error("host endpoint did not become ready")
}

fun state(client: HttpClient, base: String): Map<String, Int> =
    response(client, get(base, "/state")).lineSequence().associate {
        val fields = it.split('=', limit = 2)
        check(fields.size == 2)
        fields[0] to fields[1].toInt()
    }

fun waitForGate(client: HttpClient, base: String) {
    val deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos()
    while (System.nanoTime() < deadline) {
        if (response(client, get(base, "/gate")).toInt() == 4) return
        Thread.sleep(20)
    }
    error("four participants did not reach the gate")
}

data class Wave(val arrivals: List<Arrival>, val expected: Map<String, String>)

fun wave(base: String, round: Int, scenario: String): Wave {
    val expected = linkedMapOf<String, String>()
    val arrivals = List(4) { index ->
        val id = "r$round-i$index"
        val rollback = scenario == "mixed-wave" && index % 2 == 1
        expected[id] = if (rollback) "rolled_back" else "committed"
        Arrival(index * 25L, post(base, "/work?id=$id&rollback=$rollback", "", Duration.ofSeconds(30)))
    }
    return Wave(arrivals, expected)
}

fun experiment(program: File, scenario: String): Boolean {
    val port = freePort()
    val environment = programEnvironment(program) + ("APP_PORT" to port.toString())
    val stack = ComposeStack(File("compose.yaml"), "spring-pool", environment)
    try {
        stack.up(60)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            awaitHost(client, base)
            val outcomes = mutableListOf<Outcome>()
            val expected = linkedMapOf<String, String>()
            var violations = 0
            val rounds = if (scenario == "mixed-wave") 2 else 1
            repeat(rounds) { round ->
                response(client, post(base, "/batch", "round-$round"))
                val wave = wave(base, round, scenario)
                expected.putAll(wave.expected)
                Executors.newSingleThreadExecutor().use { executor ->
                    val load = executor.submit(Callable { runLoad(client, wave.arrivals) })
                    waitForGate(client, base)
                    response(client, post(base, "/release", ""))
                    outcomes.addAll(load.get(35, TimeUnit.SECONDS))
                }
                val snapshot = state(client, base)
                if (snapshot.getValue("ready") != 4 || snapshot.getValue("finished") != 4) violations++
            }
            val snapshot = state(client, base)
            val records = response(client, get(base, "/records")).lineSequence().filter { it.isNotBlank() }.toList()
            val expectedRecords = expected.flatMap { (id, result) ->
                if (result == "committed") listOf("audit|$id", "order|$id") else listOf("audit|$id")
            }
            if (records.toSet() != expectedRecords.toSet() || records.size != expectedRecords.size) violations++
            val events = response(client, get(base, "/events")).lineSequence().filter { it.isNotBlank() }.map { it.split('|') }.toList()
            val goodEvents = events.filter { row ->
                row.size == 4 && row[0] in expected && row[1].isNotBlank() &&
                    row[2].toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true && row[3] == "none"
            }.map { it[0] }.toSet().size
            val correctResponses = outcomes.count { outcome ->
                val id = outcome.arrival.request.uri().query.substringAfter("id=").substringBefore('&')
                outcome.error == null && outcome.status == 200 && outcome.body == expected.getValue(id)
            }
            val p95 = percentile(outcomes.map { it.elapsedMillis }, 0.95)
            val remaining = snapshot.getValue("active") + snapshot.getValue("waiting")
            if (correctResponses != expected.size) violations++
            if (snapshot.getValue("audits") != expected.size) violations++
            if (snapshot.getValue("orders") != expected.values.count { it == "committed" }) violations++
            if (snapshot.getValue("maximum") !in 1..4) violations++
            if (snapshot.getValue("peak_active") !in 0..4 || snapshot.getValue("total") !in 0..4) violations++
            if (remaining != 0) violations++
            if (goodEvents != expected.size || events.size != expected.size) violations++
            if (p95 > 12_000 || outcomes.any { it.error != null }) violations++
            metric("responses", correctResponses)
            metric("audits", snapshot.getValue("audits"))
            metric("orders", snapshot.getValue("orders"))
            metric("maximum", snapshot.getValue("maximum"))
            metric("peak_active", snapshot.getValue("peak_active"))
            metric("peak_waiting", snapshot.getValue("peak_waiting"))
            metric("remaining", remaining)
            metric("events", goodEvents)
            metric("p95_ms", p95)
            metric("violations", violations)
            return violations == 0
        }
    } finally {
        stack.close()
    }
}

val result = try {
    require(args.size == 2)
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile)
    require(args[1] in setOf("first-wave", "mixed-wave"))
    requireDocker()
    if (experiment(program, args[1])) 0 else 1
} catch (error: Exception) {
    System.err.println("experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
