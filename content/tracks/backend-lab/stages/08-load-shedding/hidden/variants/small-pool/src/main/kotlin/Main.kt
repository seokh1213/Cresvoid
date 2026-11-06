import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private fun HttpExchange.reply(status: Int, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    if (status == 503) responseHeaders.set("Retry-After", "1")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}

private fun serveWork(exchange: HttpExchange, controller: AdmissionController, backend: WorkService) {
    val deadlineMillis = exchange.requestHeaders.getFirst("X-Deadline-Epoch-Ms")?.toLongOrNull()
    val id = exchange.requestHeaders.getFirst("X-Request-Id")
    if (deadlineMillis == null || id == null || !id.matches(Regex("[a-zA-Z0-9-]{1,64}"))) {
        exchange.reply(400, "invalid")
        return
    }
    val nowMillis = System.currentTimeMillis()
    val remainingMillis = deadlineMillis.coerceIn(nowMillis - 60_000, nowMillis + 60_000) - nowMillis
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(remainingMillis)
    val accepted = controller.handle(deadlineNanos) { backend.perform(deadlineNanos) }
    if (accepted) exchange.reply(200, "done:$id") else exchange.reply(503, "busy")
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val backend = WorkService()
    val controller = AdmissionController(backend)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1024)
    server.executor = executor
    server.createContext("/") { exchange ->
        try {
            when (exchange.requestMethod to exchange.requestURI.path) {
                "GET" to "/health" -> exchange.reply(200, "ready")
                "GET" to "/stats" -> exchange.reply(200, backend.snapshot())
                "POST" to "/calibrate" -> exchange.reply(200, backend.calibrate().toString())
                "POST" to "/reset" -> {
                    backend.reset()
                    exchange.reply(200, "reset")
                }
                "POST" to "/work" -> serveWork(exchange, controller, backend)
                else -> exchange.reply(404, "missing")
            }
        } catch (_: Exception) {
            runCatching { exchange.reply(500, "error") }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}
