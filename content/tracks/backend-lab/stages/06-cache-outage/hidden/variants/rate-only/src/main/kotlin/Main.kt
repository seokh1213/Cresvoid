import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val cache = ProductCache(System.getenv("ORIGIN_URL"), System.getenv("REDIS_PORT").toInt())
    val workers = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", System.getenv("PORT").toInt()), 256)
    server.executor = workers
    server.createContext("/health") { exchange -> exchange.reply(200, "ready") }
    server.createContext("/products/") { exchange ->
        val id = exchange.requestURI.path.removePrefix("/products/").toIntOrNull()
        when {
            exchange.requestMethod != "GET" -> exchange.reply(405, "method")
            id == null || id !in 1..41 -> exchange.reply(404, "missing")
            else -> {
                val product = cache.get(id)
                if (product == null) {
                    exchange.responseHeaders.set("Retry-After", "1")
                    exchange.reply(503, "unavailable")
                } else {
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.reply(200, product)
                }
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        workers.close()
        cache.close()
    })
    server.start()
}

private fun HttpExchange.reply(status: Int, body: String) {
    use {
        val bytes = body.toByteArray(Charsets.UTF_8)
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }
}
