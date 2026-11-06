@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.util.concurrent.Callable
import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.system.exitProcess

fun snapshot(client: HttpClient, base: String): Map<String, Int> {
    val response = client.send(get(base, "/metrics"), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "metrics unavailable" }
    return response.body().lineSequence().associate { line ->
        val fields = line.split('=', limit = 2)
        check(fields.size == 2) { "invalid metrics row" }
        fields[0] to fields[1].toInt()
    }
}

fun control(client: HttpClient, base: String, action: String) {
    val response = client.send(post(base, "/fixture/$action", ""), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "fixture unavailable" }
}

data class Wave(val outcomes: List<Outcome>, val settled: Boolean, val pendingPeak: Int)

fun heldWave(client: HttpClient, base: String, firstId: Int): Wave {
    control(client, base, "hold")
    val before = snapshot(client, base).getValue("received")
    val arrivals = steady(40, 1.2) { index ->
        get(base, "/quote/${firstId + index}", Duration.ofSeconds(30))
    }
    var settled = false
    var pendingPeak = 0
    Executors.newSingleThreadExecutor().use { executor ->
        val load = executor.submit(Callable { runLoad(client, arrivals) })
        try {
            val deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos()
            while (System.nanoTime() < deadline) {
                val measured = snapshot(client, base)
                pendingPeak = maxOf(pendingPeak, measured.getValue("pool_pending"))
                val arrived = measured.getValue("received") == before + arrivals.size
                val accounted = measured.getValue("finished") + measured.getValue("active") == measured.getValue("received")
                if (arrived && accounted) {
                    settled = true
                    break
                }
                Thread.sleep(20)
            }
        } finally {
            control(client, base, "release")
        }
        return Wave(load.get(35, TimeUnit.SECONDS), settled, pendingPeak)
    }
}

fun validResponse(outcome: Outcome): Boolean {
    val id = outcome.arrival.request.uri().path.substringAfterLast('/').toInt()
    return when (outcome.status) {
        200 -> outcome.body == "$id:${id + 107}"
        429 -> outcome.body == "busy"
        else -> false
    }
}

fun reportProbeFailure(output: String) {
    val expected = setOf("permit_recovery", "db_interruptions", "db_recovery",
        "downstream_interruptions", "downstream_recovery", "sql_exceptions")
    val rows = output.lineSequence().filter { it.startsWith("METRIC ") }.map { line ->
        val fields = line.split(' ')
        check(fields.size == 3) { "invalid probe metric" }
        fields[1] to fields[2].toInt()
    }.toList()
    check(rows.size == expected.size && rows.map { it.first }.toSet() == expected) { "missing probe metrics" }
    rows.forEach { (name, value) -> metric(name, value) }
}

fun measure(client: HttpClient, base: String): Boolean {
    val warm = listOf(0, 3, 2147483539, 2147483540).flatMap { id ->
        runLoad(client, listOf(Arrival(0, get(base, "/quote/$id"))))
    }
    val waves = listOf(heldWave(client, base, 0), heldWave(client, base, 100))
    val recovery = runLoad(client, steady(5, 1.6) { index -> get(base, "/quote/${1000 + index}") })
    val final = snapshot(client, base)
    val burst = waves.flatMap { it.outcomes }
    val all = warm + burst + recovery
    val burstSuccess = burst.count { it.status == 200 }
    val rejected = burst.count { it.status == 429 }
    val recovered = recovery.count { it.status == 200 }
    val succeeded = all.count { it.status == 200 }
    val imbalance = abs(final.getValue("queries") - succeeded) + abs(final.getValue("calls") - succeeded) +
        final.getValue("active") + final.getValue("pool_active")
    val invalid = all.count { !validResponse(it) } + waves.count { !it.settled }
    val pending = waves.maxOf { it.pendingPeak }
    metric("burst_success", burstSuccess)
    metric("burst_rejected", rejected)
    metric("recovery_success", recovered)
    metric("downstream_peak", final.getValue("peak"))
    metric("pool_wait_peak", pending)
    metric("virtual_requests", final.getValue("virtual"))
    metric("work_imbalance", imbalance)
    metric("invalid_responses", invalid)
    metric("received", final.getValue("received"))
    metric("p95_ms", percentile(all.map { it.elapsedMillis }, 0.95))
    return waves.all { wave -> wave.outcomes.count { it.status == 200 } == 4 && wave.settled } &&
        burstSuccess == 8 && rejected == 88 && recovered == 8 && warm.all { it.status == 200 } &&
        final.getValue("peak") == 4 && pending == 0 && imbalance == 0 && invalid == 0 &&
        final.getValue("virtual") == 108 && final.getValue("received") == 108 && final.getValue("finished") == 108
}

fun experiment(): Int {
    require(args.size == 2 && args[1] == "quote-arrivals") { "expected program path and quote-arrivals" }
    val program = File(args[0]).canonicalFile
    require(program.isAbsolute && File(program, "build/app.jar").isFile) { "build program first" }
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    val environment = programEnvironment(program) + ("APP_PORT" to port.toString())
    val stack = ComposeStack(File("compose.yaml"), "spring-07-virtual-threads", environment)
    try {
        val probe = stack.run("probe")
        check(probe.code in 0..1) { "permit probe failed to execute: ${probe.output.takeLast(1000)}" }
        if (probe.code == 1) {
            reportProbeFailure(probe.output)
            return 1
        }
        stack.up(90)
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
            check(client.send(get(base, "/health"), HttpResponse.BodyHandlers.ofString()).statusCode() == 200)
            return if (measure(client, base)) 0 else 1
        }
    } finally {
        stack.close()
    }
}

val result = try {
    experiment()
} catch (failure: Exception) {
    System.err.println("experiment error: ${failure.javaClass.simpleName}: ${failure.message}")
    2
}
exitProcess(result)
