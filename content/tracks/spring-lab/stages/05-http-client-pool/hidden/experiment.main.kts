@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class Source : AutoCloseable {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = ServerSocket(0, 128)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val validRoutes = AtomicBoolean(true)
    val calls = AtomicInteger()
    val active = AtomicInteger()
    val peak = AtomicInteger()
    val ids = ConcurrentHashMap.newKeySet<String>()
    val connections = ConcurrentHashMap.newKeySet<Int>()
    val port: Int get() = server.localPort
    private val closed = AtomicBoolean()
    private val cleanup = Thread { close() }

    init {
        executor.submit {
            try {
                while (!closed.get()) {
                    val socket = server.accept()
                    sockets.add(socket)
                    executor.submit { serve(socket) }
                }
            } catch (error: IOException) {
                if (!closed.get()) throw error
            }
        }
        Runtime.getRuntime().addShutdownHook(cleanup)
    }

    private fun serve(socket: Socket) {
        try {
            socket.use {
                val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                while (!closed.get()) {
                    val request = input.readLine() ?: return
                    if (request.substringBefore('?') != "GET /reply") validRoutes.set(false)
                    while (!input.readLine().isNullOrEmpty()) { }
                    val query = request.substringAfter('?').substringBefore(' ').split('&').associate {
                        it.substringBefore('=') to it.substringAfter('=')
                    }
                    if (!reply(socket, query)) return
                }
            }
        } catch (_: IOException) {
            // Clients may discard a connection after a body read fails.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            sockets.remove(socket)
        }
    }

    private fun reply(socket: Socket, query: Map<String, String>): Boolean {
        val id = query.getValue("id")
        val mode = query.getValue("mode")
        calls.incrementAndGet()
        ids.add(id)
        connections.add(socket.port)
        peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
        try {
            Thread.sleep(if (mode == "hold") 2000 else 100)
            val bytes = ("$id|" + "x".repeat(8192)).toByteArray()
            val status = if (mode == "error") "503 Unavailable" else "200 OK"
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
            if (mode == "eof" || mode == "stall") {
                output.write(bytes, 0, 1)
                output.flush()
                if (mode == "stall") Thread.sleep(30_000)
                return false
            }
            output.write(bytes)
            output.flush()
            return true
        } finally {
            active.decrementAndGet()
        }
    }

    fun calledExactly(outcomes: List<Outcome>): Boolean {
        val expectedIds = outcomes.map { outcome ->
            outcome.arrival.request.uri().rawQuery.substringAfter("id=").substringBefore('&')
        }.toSet()
        return validRoutes.get() && calls.get() == outcomes.size && ids == expectedIds
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.close()
        sockets.forEach { it.close() }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        try {
            Runtime.getRuntime().removeShutdownHook(cleanup)
        } catch (_: IllegalStateException) {
        }
    }
}

fun stats(client: HttpClient, base: String): Map<String, Long> {
    val result = client.send(get(base, "/metrics"), HttpResponse.BodyHandlers.ofString())
    check(result.statusCode() == 200) { "Metrics endpoint unavailable" }
    return result.body().trim().lines().associate { line ->
        line.substringBefore('=') to line.substringAfter('=').toLong()
    }
}

fun request(base: String, id: String, mode: String = "full") =
    get(base, "/fetch?id=$id&mode=$mode", Duration.ofSeconds(8))

fun correct(outcome: Outcome): Boolean {
    val query = outcome.arrival.request.uri().rawQuery.split('&').associate {
        it.substringBefore('=') to it.substringAfter('=')
    }
    val expected = if (query.getValue("mode") == "error") 502 else 200
    return outcome.status == expected && outcome.body == query.getValue("id") && outcome.error == null
}

fun bounded(metrics: Map<String, Long>): Boolean =
    metrics.getValue("maxTotal") in 1..24 && metrics.getValue("maxPerRoute") in 1..24 &&
        metrics.getValue("opened") <= 24 && metrics.getValue("peak") <= 24

fun mixed(client: HttpClient, base: String, source: Source): Boolean {
    val warm = runLoad(client, steady(10, 1.0) { request(base, "warm-$it") })
    val regular = runLoad(client, steady(100, 3.0) { request(base, "regular-$it") })
    val mixed = runLoad(client, steady(30, 2.0) {
        request(base, "mixed-$it", listOf("full", "peek", "error")[it % 3])
    })
    Thread.sleep(200)
    val snapshot = stats(client, base)
    val outcomes = warm + regular + mixed
    val successes = outcomes.count(::correct)
    val p95 = percentile(regular.map { it.elapsedMillis }, 0.95)
    val drained = snapshot.getValue("leased") == 0L && snapshot.getValue("pending") == 0L
    val budget = bounded(snapshot) && source.connections.size <= 24 && source.peak.get() <= 24
    metric("correct_responses", successes)
    metric("incorrect_responses", outcomes.size - successes)
    metric("regular_p95_ms", p95)
    metric("source_calls", source.calls.get())
    metric("source_peak", source.peak.get())
    metric("open_connections", snapshot.getValue("opened"))
    metric("leased_after", snapshot.getValue("leased"))
    metric("pending_after", snapshot.getValue("pending"))
    metric("lease_timeouts", snapshot.getValue("timeouts"))
    metric("budget_ok", if (budget) 1 else 0)
    return successes == 370 && p95 <= 1000 && source.calledExactly(outcomes) &&
        source.peak.get() >= 8 && drained && budget && snapshot.getValue("timeouts") == 0L
}

fun burst(client: HttpClient, base: String, source: Source): Boolean {
    val outcomes = runLoad(client, List(64) { Arrival(0, request(base, "burst-$it", "hold")) })
    val rejected = outcomes.filter { it.status == 504 }
    val rejectP95 = percentile(rejected.map { it.elapsedMillis }, 0.95)
    val successful = outcomes.count(::correct)
    val validRejections = rejected.all {
        it.body == "POOL_TIMEOUT:" + it.arrival.request.uri().rawQuery.substringAfter("id=").substringBefore('&')
    }
    val afterBurst = stats(client, base)
    val beforeRecovery = source.calls.get()
    val recovery = runLoad(client, steady(40, 1.0) { request(base, "recovery-$it") })
    Thread.sleep(200)
    val snapshot = stats(client, base)
    val recoveryP95 = percentile(recovery.map { it.elapsedMillis }, 0.95)
    val budget = bounded(snapshot) && source.connections.size <= 24 && source.peak.get() <= 24
    val drained = listOf(afterBurst, snapshot).all {
        it.getValue("leased") == 0L && it.getValue("pending") == 0L
    }
    metric("rejected", rejected.size)
    metric("timeout_p95_ms", rejectP95)
    metric("recovery_correct", recovery.count(::correct))
    metric("recovery_p95_ms", recoveryP95)
    metric("source_calls", source.calls.get())
    metric("source_peak", source.peak.get())
    metric("leased_after", snapshot.getValue("leased"))
    metric("pending_after", snapshot.getValue("pending"))
    metric("lease_timeouts", snapshot.getValue("timeouts"))
    metric("budget_ok", if (budget) 1 else 0)
    return rejected.size >= 40 && successful + rejected.size == 64 && validRejections &&
        rejectP95 <= 1200 && beforeRecovery == successful && recovery.all(::correct) &&
        source.calls.get() - beforeRecovery == 40 && source.calledExactly(outcomes.filter(::correct) + recovery) &&
        recoveryP95 <= 1000 && drained && budget && snapshot.getValue("timeouts") == rejected.size.toLong()
}

fun interrupted(client: HttpClient, base: String, source: Source): Boolean {
    val observations = listOf("eof", "stall").map { mode ->
        val outcome = runLoad(client, listOf(Arrival(0,
            get(base, "/fetch?id=fault-$mode&mode=$mode", Duration.ofSeconds(20)),
        ))).single()
        Thread.sleep(200)
        outcome to stats(client, base)
    }
    val faultCorrect = observations.count { (outcome, _) ->
        val id = outcome.arrival.request.uri().rawQuery.substringAfter("id=").substringBefore('&')
        outcome.error == null && outcome.status == 502 && outcome.body == "UPSTREAM_IO:$id"
    }
    val faultLeased = observations.maxOf { it.second.getValue("leased") }
    val faultPending = observations.maxOf { it.second.getValue("pending") }
    val beforeRecovery = source.calls.get()
    val recovery = runLoad(client, steady(40, 1.0) { request(base, "recovery-$it") })
    Thread.sleep(200)
    val snapshot = stats(client, base)
    val recoveryP95 = percentile(recovery.map { it.elapsedMillis }, 0.95)
    val budget = bounded(snapshot) && source.connections.size <= 24 && source.peak.get() <= 24
    metric("fault_correct", faultCorrect)
    metric("fault_leased", faultLeased)
    metric("fault_pending", faultPending)
    metric("recovery_correct", recovery.count(::correct))
    metric("recovery_p95_ms", recoveryP95)
    metric("source_calls", source.calls.get())
    metric("leased_after", snapshot.getValue("leased"))
    metric("pending_after", snapshot.getValue("pending"))
    metric("lease_timeouts", snapshot.getValue("timeouts"))
    metric("budget_ok", if (budget) 1 else 0)
    return faultCorrect == 2 && faultLeased == 0L && faultPending == 0L && beforeRecovery == 2 &&
        recovery.all(::correct) && recoveryP95 <= 1000 && source.calls.get() == 42 &&
        source.calledExactly(observations.map { it.first } + recovery) && snapshot.getValue("leased") == 0L &&
        snapshot.getValue("pending") == 0L && snapshot.getValue("timeouts") == 0L && budget
}

fun experiment(): Boolean {
    require(args.size == 2 && File(args[0]).isAbsolute && File(args[0], "build/app.jar").isFile)
    require(args[1] in setOf("weekday-traffic", "evening-traffic", "interrupted-traffic"))
    Source().use { source ->
        val environment = programEnvironment(File(args[0])) + ("UPSTREAM_PORT" to source.port.toString())
        ComposeStack(File("compose.yaml"), "spring-http-pool", environment).use { stack ->
            try {
                stack.up(90)
                val base = "http://127.0.0.1:${stack.port("app", 8080)}"
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
                    check(client.send(get(base, "/health"), HttpResponse.BodyHandlers.ofString()).statusCode() == 200)
                    return when (args[1]) {
                        "weekday-traffic" -> mixed(client, base, source)
                        "evening-traffic" -> burst(client, base, source)
                        else -> interrupted(client, base, source)
                    }
                }
            } finally {
                stack.close()
            }
        }
    }
}

val result = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
