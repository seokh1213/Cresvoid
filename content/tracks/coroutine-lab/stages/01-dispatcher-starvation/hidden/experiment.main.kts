import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.system.exitProcess

data class Arrival(val path: String, val afterMillis: Long)
data class Observation(val path: String, val status: Int, val elapsedMillis: Double)

fun request(uri: URI): HttpRequest = HttpRequest.newBuilder(uri)
    .timeout(Duration.ofSeconds(5))
    .GET()
    .build()

fun awaitReady(client: HttpClient, base: String, server: Process) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline && server.isAlive) {
        val ready = runCatching {
            val response = client.send(request(URI.create("$base/health")), HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200 && response.body() == "ok"
        }.getOrDefault(false)
        if (ready) return
        Thread.sleep(50)
    }
    error("service did not become ready")
}

fun arrivals(): List<Arrival> = buildList {
    repeat(12) { add(Arrival("/blocking", it * 20L)) }
    repeat(20) { add(Arrival("/fast", 40L + it * 30L)) }
}.sortedBy(Arrival::afterMillis)

fun observe(client: HttpClient, base: String): List<Observation> {
    val startedAt = System.nanoTime()
    val pending = arrivals().map { arrival ->
        val due = startedAt + TimeUnit.MILLISECONDS.toNanos(arrival.afterMillis)
        val waitNanos = due - System.nanoTime()
        if (waitNanos > 0) TimeUnit.NANOSECONDS.sleep(waitNanos)
        val sentAt = System.nanoTime()
        client.sendAsync(request(URI.create(base + arrival.path)), HttpResponse.BodyHandlers.discarding())
            .handle { response, failure ->
                val status = if (failure == null) response.statusCode() else -1
                Observation(arrival.path, status, (System.nanoTime() - sentAt) / 1_000_000.0)
            }
    }
    CompletableFuture.allOf(*pending.toTypedArray()).get(7, TimeUnit.SECONDS)
    return pending.map(CompletableFuture<Observation>::join)
}

fun stats(client: HttpClient, base: String): Map<String, Int> {
    val response = client.send(request(URI.create("$base/internal/stats")), HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() != 200) return emptyMap()
    return response.body().lineSequence().mapNotNull { line ->
        val parts = line.split('=', limit = 2)
        if (parts.size == 2) parts[1].toIntOrNull()?.let { parts[0] to it } else null
    }.toMap()
}

fun percentile95(values: List<Double>): Double {
    if (values.isEmpty()) return -1.0
    val sorted = values.sorted()
    return sorted[ceil(sorted.size * 0.95).toInt() - 1]
}

fun report(observations: List<Observation>, counters: Map<String, Int>): Boolean {
    val fast = observations.filter { it.path == "/fast" }
    val okCount = observations.count { it.status == 200 }
    val p95 = percentile95(fast.map(Observation::elapsedMillis))
    val started = counters["started"] ?: -1
    val completed = counters["completed"] ?: -1
    val active = counters["active"] ?: -1
    val maxActive = counters["max_active"] ?: -1
    println("METRIC fast_p95_ms $p95")
    println("METRIC http_200 $okCount")
    println("METRIC blocking_started $started")
    println("METRIC blocking_completed $completed")
    println("METRIC blocking_active $active")
    println("METRIC blocking_max_active $maxActive")
    return observations.size == 32 && fast.size == 20 && okCount == 32 &&
        p95 <= 500.0 && started == 12 && completed == 12 && active == 0 && maxActive in 1..2
}

fun runExperiment(programDir: File): Int {
    val port = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
    val base = "http://127.0.0.1:$port"
    val server = ProcessBuilder(File(programDir, "run.sh").absolutePath)
        .directory(programDir)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .apply { environment()["PORT"] = port.toString() }
        .start()
    try {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
        awaitReady(client, base, server)
        val observations = observe(client, base)
        return if (report(observations, stats(client, base))) 0 else 1
    } finally {
        server.destroy()
        if (!server.waitFor(1, TimeUnit.SECONDS)) server.destroyForcibly()
    }
}

if (args.size != 2 || args[1] != "mixed-arrivals") {
    System.err.println("Invalid experiment arguments")
    exitProcess(2)
}
try {
    exitProcess(runExperiment(File(args[0]).canonicalFile))
} catch (_: Exception) {
    System.err.println("Experiment could not complete")
    exitProcess(2)
}
