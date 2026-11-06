import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private fun HttpExchange.reply(status: Int, body: String) {
    val bytes = "$body\n".toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}

private data class Observation(val seed: Int, val order: String)

private fun observation(query: String?): Observation {
    val fields = requireNotNull(query).split('&').map { field ->
        val parts = field.split('=', limit = 2)
        require(parts.size == 2)
        parts[0] to parts[1]
    }
    require(fields.size == 2 && fields.map { it.first }.toSet() == setOf("seed", "order"))
    val values = fields.toMap()
    val seedText = values.getValue("seed")
    require(seedText.matches(Regex("[0-9]+")))
    val seed = requireNotNull(seedText.toIntOrNull())
    val order = values.getValue("order")
    require(seed in 1..100 && order in setOf("abba", "baab"))
    return Observation(seed, order)
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val observed = AtomicBoolean()
    val workers = Executors.newSingleThreadExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = workers
    server.createContext("/") { exchange ->
        val path = exchange.requestURI.path
        try {
            when {
                path !in setOf("/health", "/observe") -> exchange.reply(404, "NOT_FOUND")
                exchange.requestMethod != "GET" -> exchange.reply(405, "METHOD")
                path == "/health" -> exchange.reply(200, "ready")
                else -> {
                    val request = observation(exchange.requestURI.rawQuery)
                    if (!observed.compareAndSet(false, true)) {
                        exchange.reply(409, "USED")
                    } else {
                        val samples = benchmark(MeasurementRig(request.seed), request.order)
                        exchange.reply(200, samples.joinToString("\n") { it.line() })
                    }
                }
            }
        } catch (_: IllegalArgumentException) {
            exchange.reply(400, "INVALID")
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        workers.shutdownNow()
    })
    server.start()
}
