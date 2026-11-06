@file:Import("../../../../../lab-env/load.main.kts")

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

fun header(socket: Socket): ByteArray {
    val bytes = ByteArrayOutputStream()
    var tail = 0
    while (bytes.size() < 16384) {
        val next = socket.getInputStream().read()
        if (next < 0) throw IOException("Incomplete headers")
        bytes.write(next)
        tail = (tail shl 8) or next
        if (tail == 0x0d0a0d0a) return bytes.toByteArray()
    }
    throw IOException("Oversized headers")
}

class Rig : AutoCloseable {
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val closed = AtomicBoolean()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val origin = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
    private val proxy = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
    private val tls = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
    private val seen = ConcurrentHashMap<String, AtomicInteger>()
    private val failures = ConcurrentLinkedQueue<Exception>()
    private val cleanup = Thread { close() }
    val upstreamUrl = "http://127.0.0.1:${proxy.localPort}"
    val connectUrl = "https://127.0.0.1:${tls.localPort}/"

    init {
        Runtime.getRuntime().addShutdownHook(cleanup)
        listen(origin) { socket ->
            try {
                serveOrigin(socket)
            } catch (failure: Exception) {
                if (!closed.get()) failures.add(failure)
            }
        }
        listen(proxy, ::serveProxy)
        listen(tls) { socket ->
            if (socket.getInputStream().read() >= 0) count("connect")
            awaitDisconnect(socket)
        }
    }

    private fun listen(listener: ServerSocket, action: (Socket) -> Unit) {
        workers.submit {
            while (!closed.get()) {
                val socket = try { listener.accept() } catch (_: IOException) { break }
                sockets.add(socket)
                workers.submit {
                    try {
                        socket.soTimeout = 9000
                        socket.use(action)
                    } catch (_: IOException) {
                        // Cancellation and the drop case close sockets before a response is complete.
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (failure: Exception) {
                        failures.add(failure)
                    } finally {
                        sockets.remove(socket)
                    }
                }
            }
        }
    }

    private fun count(kind: String) {
        seen.computeIfAbsent(kind) { AtomicInteger() }.incrementAndGet()
    }

    fun snapshot(): Map<String, Int> = seen.mapValues { it.value.get() }

    fun pending(): Int = sockets.size

    fun checkHealthy() {
        check(failures.isEmpty()) { "Fixture failed: ${failures.peek()}" }
    }

    private fun awaitDisconnect(socket: Socket): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(6500)
        val input = socket.getInputStream()
        while (System.nanoTime() < deadline) {
            socket.soTimeout = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceAtLeast(1)
            try {
                if (input.read() < 0) return true
            } catch (_: SocketTimeoutException) {
                return false
            }
        }
        return false
    }

    private fun serveOrigin(socket: Socket) {
        val target = header(socket).toString(Charsets.US_ASCII).substringBefore("\r\n").split(' ')[1]
        val token = URI.create(target).rawQuery.orEmpty().substringAfter("token=")
        val body = "ok:$token".toByteArray(Charsets.US_ASCII)
        val response = "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII) + body)
    }

    private fun serveProxy(socket: Socket) {
        val request = header(socket)
        val target = request.toString(Charsets.US_ASCII).substringBefore("\r\n").split(' ')[1]
        val kind = URI.create(target).path.removePrefix("/")
        count(kind)
        if (kind == "drop") return
        val response = fetchOrigin(request)
        when (kind) {
            "headers" -> {
                if (!awaitDisconnect(socket)) socket.getOutputStream().write(response)
            }
            "body" -> drip(socket, response)
            else -> socket.getOutputStream().write(response)
        }
    }

    private fun fetchOrigin(request: ByteArray): ByteArray = try {
        Socket("127.0.0.1", origin.localPort).use { downstream ->
            downstream.soTimeout = 3000
            downstream.getOutputStream().write(request)
            downstream.getInputStream().readBytes()
        }
    } catch (failure: IOException) {
        if (!closed.get()) failures.add(failure)
        throw failure
    }

    private fun drip(socket: Socket, response: ByteArray) {
        val boundary = response.toString(Charsets.US_ASCII).indexOf("\r\n\r\n") + 4
        val output = socket.getOutputStream()
        output.write(response, 0, boundary)
        output.flush()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(6500)
        for (index in boundary until response.size) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return
            TimeUnit.NANOSECONDS.sleep(minOf(TimeUnit.MILLISECONDS.toNanos(400), remaining))
            if (System.nanoTime() >= deadline) return
            output.write(response[index].toInt())
            output.flush()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listOf(origin, proxy, tls).forEach { it.close() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        check(workers.awaitTermination(3, TimeUnit.SECONDS)) { "Fixture workers did not stop" }
        runCatching { Runtime.getRuntime().removeShutdownHook(cleanup) }
    }
}

val kinds = listOf("fast", "connect", "headers", "body", "drop")

fun requestKind(outcome: Outcome): String =
    outcome.arrival.request.uri().rawQuery.substringAfter("kind=").substringBefore('&')

fun mismatchMask(outcomes: List<Outcome>): Int =
    kinds.withIndex().sumOf { (index, kind) ->
        if (outcomes.any { requestKind(it) == kind && !correct(it) }) 1 shl index else 0
    }

fun correct(outcome: Outcome): Boolean {
    if (outcome.error != null) return false
    val query = outcome.arrival.request.uri().rawQuery
    val kind = requestKind(outcome)
    val token = query.substringAfter("token=")
    val expected = when (kind) {
        "fast" -> 200 to "ok:$token"
        "connect" -> 504 to "CONNECT_TIMEOUT"
        "headers" -> 504 to "RESPONSE_TIMEOUT"
        "body" -> 504 to "TOTAL_TIMEOUT"
        else -> 502 to "IO_ERROR"
    }
    return outcome.status == expected.first && outcome.body == expected.second
}

fun readStats(client: HttpClient, base: String): Map<String, Int> {
    val response = client.send(get(base, "/stats"), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "Stats endpoint failed" }
    return response.body().split(' ').associate { field ->
        val parts = field.split('=')
        parts[0] to parts[1].toInt()
    }
}

fun experiment(program: File, scenario: String): Boolean {
    val rate = when (scenario) {
        "first-pass" -> 10
        "return-pass" -> 20
        else -> error("Unknown scenario: $scenario")
    }
    Rig().use { rig ->
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        val environment = mapOf("PORT" to "$port", "UPSTREAM_URL" to rig.upstreamUrl, "CONNECT_URL" to rig.connectUrl)
        Program(program, environment, base).use {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
                val warmup = runLoad(client, steady(5, 0.6) {
                    get(base, "/call?kind=fast&token=warmup0000000000000000$it", Duration.ofSeconds(10))
                })
                check(warmup.all { correct(it) && it.elapsedMillis <= 4500 }) { "Warmup failed" }
                val before = rig.snapshot()
                check(client.send(get(base, "/reset"), HttpResponse.BodyHandlers.ofString()).statusCode() == 200)
                val arrivals = steady(rate, 3.0) { index ->
                    get(base, "/call?kind=${kinds[index % kinds.size]}&token=sample0000000000000000$index", Duration.ofSeconds(10))
                }
                val outcomes = runLoad(client, arrivals)
                check(outcomes.none { it.error?.endsWith("TimeoutException") == true }) {
                    "Public request exceeded the 10-second measurement limit"
                }
                val after = rig.snapshot()
                val recovery = runLoad(client, steady(10, 0.5) {
                    get(base, "/call?kind=fast&token=recovery00000000000000$it", Duration.ofSeconds(10))
                })
                check(recovery.none { it.error?.endsWith("TimeoutException") == true }) {
                    "Recovery request exceeded the measurement limit"
                }
                val recovered = recovery.count { correct(it) && it.elapsedMillis <= 4500 }
                Thread.sleep(800)
                rig.checkHealthy()
                val stats = readStats(client, base)
                val pending = rig.pending()
                val seen = kinds.count { kind ->
                    val planned = arrivals.count { it.request.uri().rawQuery.startsWith("kind=$kind&") }
                    after.getOrDefault(kind, 0) - before.getOrDefault(kind, 0) >= planned
                }
                val matched = outcomes.count(::correct)
                val bounded = outcomes.count { it.error == null && it.elapsedMillis <= 4500 }
                metric("requests", outcomes.size)
                metric("correct", matched)
                metric("within_bound", bounded)
                metric("mismatch_mask", mismatchMask(outcomes))
                metric("recovered", recovered)
                metric("peak_thread_growth", stats.getValue("peak"))
                metric("end_thread_growth", stats.getValue("end"))
                metric("active_calls", stats.getValue("active"))
                metric("upstream_seen", seen)
                metric("pending_upstreams", pending)
                return matched == outcomes.size && bounded == outcomes.size && recovered == 5 && seen == 5 &&
                    stats.getValue("peak") <= 40 && stats.getValue("end") <= 16 && stats.getValue("active") == 0 && pending == 0
            }
        }
    }
}

val result = try {
    require(args.size == 2) { "Expected absolute program path and scenario" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute()) { "Invalid program directory" }
    if (experiment(program, args[1])) 0 else 1
} catch (failure: Exception) {
    System.err.println("Experiment error: ${failure.javaClass.simpleName}: ${failure.message}")
    2
}
exitProcess(result)
