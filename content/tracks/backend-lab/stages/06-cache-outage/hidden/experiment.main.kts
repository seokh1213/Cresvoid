@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("redis-audit.main.kts")

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class RedisEndpoint(initialPort: Int) : AutoCloseable {
    private val listener = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile var targetPort = initialPort
    val port: Int get() = listener.localPort

    init {
        workers.submit {
            try {
                while (!listener.isClosed) {
                    val client = listener.accept()
                    sockets.add(client)
                    workers.submit { forward(client) }
                }
            } catch (_: java.io.IOException) { }
        }
    }

    private fun forward(client: Socket) {
        val upstream = Socket()
        sockets.add(upstream)
        try {
            upstream.connect(InetSocketAddress("127.0.0.1", targetPort), 300)
            val upload = workers.submit {
                try {
                    client.getInputStream().copyTo(upstream.getOutputStream())
                } catch (_: java.io.IOException) { }
                finally { runCatching { upstream.shutdownOutput() } }
            }
            try {
                upstream.getInputStream().copyTo(client.getOutputStream())
            } finally {
                client.close()
                upstream.close()
                upload.get()
            }
        } catch (_: java.io.IOException) { }
        finally {
            client.close()
            upstream.close()
            sockets.remove(client)
            sockets.remove(upstream)
        }
    }

    override fun close() {
        listener.close()
        sockets.forEach { it.close() }
        workers.close()
    }
}

class Source : AutoCloseable {
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 256)
    private val window = java.util.ArrayDeque<Long>()
    val attempts = ConcurrentLinkedQueue<Long>()
    val issued = ConcurrentHashMap<String, Long>()
    val rejected = AtomicInteger()
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = workers
        server.createContext("/products/") { exchange ->
            val id = exchange.requestURI.path.substringAfterLast('/').toIntOrNull()
            val allowed = synchronized(window) {
                val now = System.nanoTime()
                attempts.add(now)
                while (window.isNotEmpty() && now - window.first() >= 1_000_000_000) window.removeFirst()
                window.addLast(now)
                window.size <= 100
            }
            val status: Int
            val body: String
            if (!allowed || id == null || id !in 1..41) {
                rejected.incrementAndGet()
                status = 503
                body = "origin busy"
            } else {
                Thread.sleep(200)
                status = 200
                body = "{\"id\":$id,\"price\":${1_000 + id},\"version\":1,\"loadedAtMillis\":${System.currentTimeMillis()}}"
                issued[body] = System.nanoTime()
            }
            exchange.use {
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        }
        server.start()
    }

    override fun close() {
        server.stop(0)
        workers.close()
    }
}

class ContractAudit {
    val total = AtomicInteger()
    val first = AtomicInteger()

    fun record(code: Int, count: Int = 1) {
        if (count == 0) return
        first.compareAndSet(0, code)
        total.addAndGet(count)
    }
}

class CheckedClient(private val source: Source) : HttpClient() {
    private val delegate = newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
    val audit = ContractAudit()
    override fun cookieHandler() = delegate.cookieHandler()
    override fun connectTimeout() = delegate.connectTimeout()
    override fun followRedirects() = delegate.followRedirects()
    override fun proxy() = delegate.proxy()
    override fun sslContext() = delegate.sslContext()
    override fun sslParameters() = delegate.sslParameters()
    override fun authenticator() = delegate.authenticator()
    override fun version() = delegate.version()
    override fun executor() = delegate.executor()
    override fun <T> send(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
        val response = try {
            delegate.send(request, handler)
        } catch (failure: Exception) {
            audit.record(6)
            throw failure
        }
        val violation = when (response.statusCode()) {
            200 -> {
                val body = response.body().toString()
                val issuedAt = source.issued[body]
                val id = request.uri().path.substringAfterLast('/')
                when {
                    issuedAt == null -> 1
                    !body.startsWith("{\"id\":$id,") -> 2
                    System.nanoTime() - issuedAt > 6_500_000_000 -> 3
                    else -> 0
                }
            }
            503 -> response.headers().firstValue("Retry-After").orElse("").let {
                if (it.matches(Regex("[0-9]+")) && it.any { digit -> digit != '0' }) 0 else 4
            }
            else -> 5
        }
        if (violation != 0) audit.record(violation)
        return response
    }
    override fun <T> sendAsync(request: HttpRequest, handler: HttpResponse.BodyHandler<T>) =
        delegate.sendAsync(request, handler)
    override fun <T> sendAsync(request: HttpRequest, handler: HttpResponse.BodyHandler<T>, push: HttpResponse.PushPromiseHandler<T>) =
        delegate.sendAsync(request, handler, push)
    override fun close() = delegate.close()
}

fun redisCommand(stack: ComposeStack, vararg arguments: String) {
    val process = ProcessBuilder(listOf("docker", "compose", "-p", stack.project, "-f", "compose.yaml") + arguments)
        .apply { environment().putAll(LAB_IMAGES) }
        .redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "Redis 제어 실패: $output" }
}

fun sleepUntil(start: Long, millis: Long) {
    val remaining = millis - (System.nanoTime() - start) / 1_000_000
    if (remaining > 0) Thread.sleep(remaining)
}

fun rollingPeak(instants: List<Long>): Int {
    var left = 0
    var peak = 0
    for (right in instants.indices) {
        while (instants[right] - instants[left] >= 1_000_000_000) left++
        peak = maxOf(peak, right - left + 1)
    }
    return peak
}

fun sampleRedisTtls(start: Long, endpoint: RedisEndpoint, available: AtomicBoolean, audit: ContractAudit) {
    for (tick in 0L..200L) {
        sleepUntil(start, tick * 200)
        if (!available.get()) continue
        try {
            audit.record(7, redisTtlViolations(endpoint.targetPort))
        } catch (failure: java.io.IOException) {
            if (available.get()) throw failure
        }
    }
}

fun experiment(program: File): Boolean {
    ComposeStack(File("compose.yaml"), "backend-cache-outage").use { stack ->
        stack.up()
        Source().use { source ->
            RedisEndpoint(stack.port("redis", 6379)).use { endpoint ->
                return measure(program, stack, source, endpoint)
            }
        }
    }
}

fun measure(program: File, stack: ComposeStack, source: Source, endpoint: RedisEndpoint): Boolean {
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    Program(program, mapOf("PORT" to "$port", "ORIGIN_URL" to source.url,
        "REDIS_PORT" to endpoint.port.toString()), base).use {
        CheckedClient(source).use { client ->
            return measureTraffic(client, base, stack, source, endpoint)
        }
    }
}

fun measureTraffic(client: CheckedClient, base: String, stack: ComposeStack, source: Source, endpoint: RedisEndpoint): Boolean {
    val schedule = steady(500, 40.0) { index ->
        val id = if (index % 5 != 4) 1 else 2 + (index / 5) % 40
        get(base, "/products/$id", Duration.ofSeconds(5))
    }
    val start = System.nanoTime()
    var recoveredAt = 0L
    val redisAvailable = AtomicBoolean(true)
    val outcomes = Executors.newVirtualThreadPerTaskExecutor().use { controls ->
        val audit = controls.submit {
            sampleRedisTtls(start, endpoint, redisAvailable, client.audit)
        }
        val fault = controls.submit {
            sleepUntil(start, 8_000)
            redisAvailable.set(false)
            check(stack.pause("redis").code == 0)
            sleepUntil(start, 10_000)
            check(stack.unpause("redis").code == 0)
            redisCommand(stack, "kill", "-s", "SIGKILL", "redis")
            sleepUntil(start, 25_000)
            redisCommand(stack, "start", "redis")
            endpoint.targetPort = stack.port("redis", 6379)
            val deadline = System.nanoTime() + 5_000_000_000
            while (stack.exec("redis", "redis-cli", "ping").trim() != "PONG") {
                check(System.nanoTime() < deadline) { "Redis 복구 준비 실패" }
                Thread.sleep(100)
            }
            recoveredAt = (System.nanoTime() - start) / 1_000_000
            redisAvailable.set(true)
        }
        val results = runLoad(client, schedule)
        fault.get()
        audit.get()
        results
    }
    val measured = outcomes.filter { it.arrival.atMillis >= 5_000 }
    val outage = measured.filter { it.arrival.atMillis in 10_000 until recoveredAt }
    val recovered = measured.filter { it.arrival.atMillis >= recoveredAt + 5_000 }
    check(outage.isNotEmpty() && recovered.size >= 1_000) { "측정 구간 부족" }
    val errors = outage.count { it.status != 200 }
    val recoveryErrors = recovered.count { it.status != 200 }
    val slow = measured.count { it.elapsedMillis > 3_000 }
    val peak = rollingPeak(source.attempts.toList().sorted())
    val hot = stack.exec("redis", "redis-cli", "--raw", "GET", "product:1").trim()
    if (!source.issued.containsKey(hot)) client.audit.record(8)
    val violations = client.audit.total.get()
    metric("origin_peak_1s", peak)
    metric("outage_error_pct", errors * 100.0 / outage.size)
    metric("recovery_errors", recoveryErrors)
    metric("responses_over_3s", slow)
    metric("contract_violations", violations)
    metric("first_contract_violation", client.audit.first.get())
    metric("origin_rejections", source.rejected.get())
    metric("response_p99_ms", percentile(measured.map { it.elapsedMillis }, 0.99))
    metric("completed_requests", outcomes.size)
    metric("redis_recovery_ms", recoveredAt - 25_000)
    return peak <= 120 && errors * 10 <= outage.size * 3 && recoveryErrors == 0 &&
        slow == 0 && violations == 0 && outcomes.size == 20_000
}

try {
    require(args.size == 2 && args[1] == "catalog-traffic") { "프로그램 경로와 시나리오 이름을 지정한다" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    requireDocker()
    exitProcess(if (experiment(program)) 0 else 1)
} catch (failure: Exception) {
    System.err.println("실험 오류: ${failure.message}")
    exitProcess(2)
}
