// Shared load and measurement helpers for lab experiments.
// Import from a stage's hidden/experiment.main.kts with:
//   @file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A request sent [atMillis] after the load starts, whatever happened to earlier requests. */
data class Arrival(val atMillis: Long, val request: HttpRequest)

data class Outcome(val arrival: Arrival, val status: Int, val elapsedMillis: Double, val body: String, val error: String?)

fun get(base: String, path: String, timeout: Duration = Duration.ofSeconds(5)): HttpRequest =
    HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout).GET().build()

fun post(base: String, path: String, body: String, timeout: Duration = Duration.ofSeconds(5), headers: Map<String, String> = emptyMap()): HttpRequest =
    HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout)
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()

/** Evenly spaced arrivals at [perSecond] for [seconds]. Use it to build open-model schedules. */
fun steady(perSecond: Int, seconds: Double, startMillis: Long = 0, request: (index: Int) -> HttpRequest): List<Arrival> {
    val count = (perSecond * seconds).toInt()
    return List(count) { index -> Arrival(startMillis + index * 1000L / perSecond, request(index)) }
}

/**
 * Open-model load: each arrival fires at its scheduled time on its own virtual thread, so a slow service does not
 * slow the arrival rate down. That is what makes overload, retries and stampedes visible.
 */
fun runLoad(client: HttpClient, arrivals: List<Arrival>): List<Outcome> {
    val start = System.nanoTime()
    Executors.newVirtualThreadPerTaskExecutor().use { executor ->
        val futures = arrivals.sortedBy { it.atMillis }.map { arrival ->
            executor.submit<Outcome> {
                val wait = arrival.atMillis - (System.nanoTime() - start) / 1_000_000
                if (wait > 0) Thread.sleep(wait)
                send(client, arrival)
            }
        }
        return futures.map { it.get() }
    }
}

private fun send(client: HttpClient, arrival: Arrival): Outcome {
    val began = System.nanoTime()
    return try {
        val response = client.send(arrival.request, HttpResponse.BodyHandlers.ofString())
        Outcome(arrival, response.statusCode(), (System.nanoTime() - began) / 1e6, response.body(), null)
    } catch (error: Exception) {
        Outcome(arrival, 0, (System.nanoTime() - began) / 1e6, "", error.javaClass.simpleName)
    }
}

fun percentile(values: List<Double>, fraction: Double): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val rank = Math.ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

/** METRIC lines are the only standard output the engine shows; at most ten per run. */
fun metric(name: String, value: Number) {
    println("METRIC $name ${value.toDouble().let { if (it == Math.rint(it)) it.toLong().toString() else "%.3f".format(it) }}")
}

/** Starts the learner's program with run.sh and waits until [readyPath] answers 200. Stops it on exit. */
class Program(programDir: File, environment: Map<String, String>, private val base: String, readyPath: String = "/health") : AutoCloseable {
    private val process: Process = ProcessBuilder(File(programDir, "run.sh").absolutePath)
        .directory(programDir)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .apply { environment().putAll(environment) }
        .start()
    private val cleanup = Thread { stop() }

    init {
        Runtime.getRuntime().addShutdownHook(cleanup)
        awaitReady(readyPath)
    }

    private fun awaitReady(path: String) {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build()
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) error("프로그램이 준비되기 전에 끝났다(종료 코드 ${process.exitValue()})")
            val ready = runCatching { client.send(get(base, path, Duration.ofMillis(500)), HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }
            if (ready.getOrDefault(false)) return
            Thread.sleep(100)
        }
        error("프로그램이 30초 안에 준비되지 않았다")
    }

    private fun stop() {
        process.descendants().forEach { it.destroy() }
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }

    override fun close() {
        stop()
        try {
            Runtime.getRuntime().removeShutdownHook(cleanup)
        } catch (_: IllegalStateException) {
        }
    }
}

/** A free local port for the learner's program, so parallel runs do not collide. */
fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }
