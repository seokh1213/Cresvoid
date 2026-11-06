@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("admission-checks.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

fun control(client: HttpClient, base: String, path: String): String {
    val response = client.send(post(base, path, "", Duration.ofSeconds(15)), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "control request failed: $path" }
    return response.body()
}

fun stats(client: HttpClient, base: String): Map<String, Long> {
    val response = client.send(get(base, "/stats"), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200)
    return response.body().lines().associate { line ->
        val parts = line.split("=", limit = 2)
        check(parts.size == 2)
        parts[0] to parts[1].toLong()
    }
}

fun schedule(base: String, profile: String): List<Arrival> {
    val epoch = System.currentTimeMillis()
    val rates = when (profile) {
        "steady-arrivals" -> listOf(180, 180, 180, 180)
        "changing-arrivals" -> listOf(100, 200, 120, 180)
        else -> error("unknown scenario")
    }
    return rates.flatMapIndexed { phase, rate ->
        steady(rate, 2.0, phase * 2000L) { index ->
            val at = phase * 2000L + index * 1000L / rate
            post(base, "/work", "", Duration.ofSeconds(25), mapOf(
                "X-Request-Id" to "load-$phase-$index",
                "X-Deadline-Epoch-Ms" to (epoch + at + 1200).toString(),
            ))
        }
    }
}

fun probes(client: HttpClient, base: String): List<Outcome> {
    val epoch = System.currentTimeMillis()
    val arrivals = List(16) { index ->
        Arrival(0, post(base, "/work", "", headers = mapOf(
            "X-Request-Id" to "hold-$index", "X-Deadline-Epoch-Ms" to (epoch + 1200).toString(),
        )))
    } + List(8) { index ->
        Arrival(40, post(base, "/work", "", headers = mapOf(
            "X-Request-Id" to "short-$index", "X-Deadline-Epoch-Ms" to (epoch + 100).toString(),
        )))
    } + List(32) { index ->
        Arrival(800, post(base, "/work", "", headers = mapOf(
            "X-Request-Id" to "old-$index", "X-Deadline-Epoch-Ms" to (epoch - 1000).toString(),
        )))
    }
    return runLoad(client, arrivals)
}

fun invalid(outcomes: List<Outcome>): Int = outcomes.count { outcome ->
    when (outcome.status) {
        200 -> outcome.body != "done:${outcome.arrival.request.headers().firstValue("X-Request-Id").orElse("")}"
        503 -> outcome.body != "busy"
        else -> true
    }
}

fun measure(client: HttpClient, base: String, profile: String, contractFailures: Int): Boolean {
    val capacity = control(client, base, "/calibrate").toDouble()
    check(capacity.isFinite() && capacity > 0)
    control(client, base, "/reset")
    val probe = probes(client, base)
    val probeFailures = contractFailures + httpContractFailures(client, base) + probe.count {
        val id = it.arrival.request.headers().firstValue("X-Request-Id").orElse("")
        (id.startsWith("old-") && it.status != 503) ||
            (id.startsWith("hold-") && it.status != 200)
    }
    val load = runLoad(client, schedule(base, profile))
    val measured = load.filter { it.arrival.atMillis >= 2000 }
    val accepted = measured.filter { it.status == 200 }
    val rejected = measured.filter { it.status == 503 }
    val goodput = accepted.count { it.elapsedMillis <= 1000 } / 6.0
    val acceptedP99 = percentile(accepted.map { it.elapsedMillis }, 0.99)
    val rejectedP99 = percentile(rejected.map { it.elapsedMillis }, 0.99)
    val recovery = runLoad(client, steady(24, 2.0) { index ->
        post(base, "/work", "", headers = mapOf(
            "X-Request-Id" to "recovery-$index",
            "X-Deadline-Epoch-Ms" to (System.currentTimeMillis() + index * 1000L / 24 + 1200).toString(),
        ))
    })
    val snapshot = stats(client, base)
    val invalidCount = invalid(probe + load + recovery)
    val recoveryFailures = recovery.count { it.status != 200 || it.elapsedMillis > 1000 }
    val requestFlags = (if (invalidCount != 0) 1 else 0) +
        (if (probeFailures != 0) 2 else 0) +
        (if (recoveryFailures != 0) 4 else 0) +
        (if (rejected.isEmpty()) 8 else 0)
    val counterFlags = finalCounterFlags(snapshot, (probe + load + recovery).count { it.status == 200 }.toLong())
    metric("capacity_rps", capacity)
    metric("goodput_rps", goodput)
    metric("accepted_p99_ms", acceptedP99)
    metric("rejected_p99_ms", rejectedP99)
    metric("peak_active", snapshot.getValue("max_active"))
    metric("peak_waiting", snapshot.getValue("max_waiting"))
    metric("expired_started", snapshot.getValue("expired_started"))
    metric("request_failure_flags", requestFlags)
    metric("final_counter_flags", counterFlags)
    return goodput >= capacity * 0.9 && acceptedP99 <= 1000 && rejectedP99 <= 500 &&
        snapshot.getValue("max_active") <= 16 &&
        snapshot.getValue("max_waiting") <= 8 && snapshot.getValue("expired_started") == 0L &&
        counterFlags == 0 && requestFlags == 0
}

val result = try {
    require(args.size == 2)
    require(args[1] in setOf("steady-arrivals", "changing-arrivals"))
    val programDir = File(args[0])
    require(programDir.isAbsolute && programDir.isDirectory)
    val contractFailures = controllerFailures(programDir = programDir)
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build().use { client ->
        Program(programDir, mapOf("PORT" to port.toString()), base).use {
            if (measure(client, base, args[1], contractFailures)) 0 else 1
        }
    }
} catch (error: Exception) {
    System.err.println("experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
