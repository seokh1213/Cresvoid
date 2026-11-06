import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class Reply(val status: Int, val body: String)

class Observations {
    private val threads = ManagementFactory.getThreadMXBean()
    private val baseline = AtomicInteger(threads.threadCount)
    private val peak = AtomicInteger(baseline.get())
    val active = AtomicInteger()

    fun sample() {
        peak.accumulateAndGet(threads.threadCount, ::maxOf)
    }

    fun reset() {
        baseline.set(threads.threadCount)
        peak.set(baseline.get())
    }

    fun snapshot(): String {
        sample()
        return "peak=${(peak.get() - baseline.get()).coerceAtLeast(0)} " +
            "end=${(threads.threadCount - baseline.get()).coerceAtLeast(0)} active=${active.get()}"
    }
}

fun respond(exchange: HttpExchange, reply: Reply) {
    val bytes = reply.body.toByteArray(Charsets.UTF_8)
    exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

fun handleCall(exchange: HttpExchange, gateway: Gateway, observations: Observations): Reply {
    val query = exchange.requestURI.rawQuery.orEmpty().split('&').associate {
        val entry = it.split('=', limit = 2)
        entry[0] to entry.getOrElse(1) { "" }
    }
    val kind = query["kind"]
    val token = query["token"].orEmpty()
    if (kind !in setOf("fast", "connect", "headers", "body", "drop") || !token.matches(Regex("[A-Za-z0-9]+"))) {
        return Reply(400, "BAD_REQUEST")
    }
    observations.active.incrementAndGet()
    return try {
        gateway.call(requireNotNull(kind), token)
    } finally {
        observations.active.decrementAndGet()
    }
}

fun startServer() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val gateway = Gateway(requireNotNull(System.getenv("UPSTREAM_URL")), requireNotNull(System.getenv("CONNECT_URL")))
    val observations = Observations()
    val workers = Executors.newVirtualThreadPerTaskExecutor()
    val sampler = Executors.newSingleThreadScheduledExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 128)
    server.executor = workers
    server.createContext("/") { exchange ->
        exchange.use {
            val reply = when (exchange.requestURI.path) {
                "/health" -> Reply(200, "ready")
                "/stats" -> Reply(200, observations.snapshot())
                "/reset" -> { observations.reset(); Reply(200, "reset") }
                "/call" -> handleCall(exchange, gateway, observations)
                else -> Reply(404, "NOT_FOUND")
            }
            respond(exchange, reply)
        }
    }
    sampler.scheduleAtFixedRate(observations::sample, 0, 20, TimeUnit.MILLISECONDS)
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        sampler.shutdownNow()
        workers.shutdownNow()
    })
    server.start()
}
