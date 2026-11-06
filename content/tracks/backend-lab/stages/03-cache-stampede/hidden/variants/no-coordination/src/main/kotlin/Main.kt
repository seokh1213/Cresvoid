import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val redis = RedisClient(
        System.getenv("REDIS_HOST") ?: "127.0.0.1",
        System.getenv("REDIS_PORT")?.toInt() ?: 6379,
    )
    val origin = Origin(redis, System.getenv("ORIGIN_DELAY_MS")?.toLong() ?: 200L)
    val cache = ProductCache(redis, origin)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", System.getenv("PORT").toInt()), 1024)
    server.executor = executor
    server.createContext("/") { exchange ->
        try {
            serve(exchange, cache)
        } catch (_: Exception) {
            reply(exchange, 500, "unavailable")
        } finally {
            exchange.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
        redis.close()
    })
    server.start()
}

private fun serve(exchange: HttpExchange, cache: ProductCache) {
    if (exchange.requestMethod != "GET") {
        reply(exchange, 404, "not found")
        return
    }
    if (exchange.requestURI.path == "/health") {
        reply(exchange, 200, "ok")
        return
    }
    val id = Regex("/products/([0-9]+)").matchEntire(exchange.requestURI.path)
        ?.groupValues?.get(1)?.toIntOrNull()
    if (id == null || id !in 1..100) {
        reply(exchange, 404, "not found")
        return
    }
    reply(exchange, 200, cache.get(id))
}

private fun reply(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", if (body.startsWith("{")) "application/json" else "text/plain")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
