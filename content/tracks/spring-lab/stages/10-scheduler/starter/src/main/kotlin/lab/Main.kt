package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class ExperimentService : AutoCloseable {
    private var context: AnnotationConfigApplicationContext? = null
    @Volatile private var probe: ScheduledProbe? = null

    @Synchronized
    fun start(mode: String): String {
        probe?.releaseJob()
        context?.close()
        val next = ScheduledProbe()
        context = openRun(mode, next)
        probe = next
        return "started"
    }

    fun snapshot(): String? = probe?.snapshot()
    fun release(): String? = probe?.let { it.releaseJob(); "released" }

    @Synchronized
    override fun close() {
        probe?.releaseJob()
        context?.close()
    }
}

fun HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val service = ExperimentService()
    val workers = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 32)
    server.executor = workers
    server.createContext("/") { exchange ->
        val method = exchange.requestMethod
        val path = exchange.requestURI.path
        when {
            path == "/health" && method == "GET" -> exchange.respond(200, "ready")
            path == "/start" && method == "POST" -> {
                val mode = exchange.requestURI.rawQuery?.removePrefix("mode=")
                if (mode !in setOf("baseline", "comparison")) exchange.respond(400, "invalid mode")
                else exchange.respond(200, service.start(requireNotNull(mode)))
            }
            path == "/snapshot" && method == "GET" ->
                service.snapshot()?.let { exchange.respond(200, it) } ?: exchange.respond(409, "not started")
            path == "/release" && method == "POST" ->
                service.release()?.let { exchange.respond(200, it) } ?: exchange.respond(409, "not started")
            else -> exchange.respond(404, "not found")
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        service.close()
        workers.shutdownNow()
    })
    server.start()
}
