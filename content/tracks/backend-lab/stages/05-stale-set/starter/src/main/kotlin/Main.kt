import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val redis = RedisClient(System.getenv("REDIS_HOST") ?: "127.0.0.1", System.getenv("REDIS_PORT").toInt())
    val source = Source()
    val cache = ProductCache(source, redis)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", System.getenv("PORT").toInt()), 128)
    val workers = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = workers
    server.createContext("/") { exchange ->
        try {
            handle(exchange, source, cache, redis)
        } catch (_: IllegalArgumentException) {
            reply(exchange, 400, "invalid request")
        } catch (error: Exception) {
            System.err.println(error.javaClass.simpleName)
            reply(exchange, 500, "request failed")
        } finally {
            exchange.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        source.release()
        server.stop(0)
        workers.close()
        redis.close()
    })
    server.start()
}

private fun handle(exchange: HttpExchange, source: Source, cache: ProductCache, redis: RedisClient) {
    val path = exchange.requestURI.path
    val method = exchange.requestMethod
    when {
        path == "/health" && method == "GET" -> reply(exchange, 200, "ready")
        path == "/admin/hold" && method == "POST" -> {
            source.hold()
            redis.delete(*(1..12).map { "product:$it" }.toTypedArray())
            reply(exchange, 200, "held")
        }
        path == "/admin/release" && method == "POST" -> {
            source.release()
            reply(exchange, 200, "released")
        }
        path == "/admin/pending" && method == "GET" -> reply(exchange, 200, source.pending().toString())
        path == "/admin/reads" && method == "GET" -> reply(exchange, 200, source.readCount().toString())
        path == "/admin/state" && method == "GET" -> {
            val rows = (1..12).joinToString("\n") { id ->
                "${source.current(id).encode()}|${redis.get("product:$id") ?: "empty"}"
            }
            reply(exchange, 200, rows)
        }
        else -> productRequest(exchange, path, cache)
    }
}

private fun productRequest(exchange: HttpExchange, path: String, cache: ProductCache) {
    val match = Regex("/products/([0-9]+)(/price)?").matchEntire(path)
    if (match == null) {
        reply(exchange, 404, "not found")
        return
    }
    val id = match.groupValues[1].toIntOrNull()
    if (id == null || id !in 1..12) {
        reply(exchange, 404, "not found")
        return
    }
    when {
        exchange.requestMethod == "GET" && match.groupValues[2].isEmpty() ->
            reply(exchange, 200, cache.get(id).encode())
        exchange.requestMethod == "PUT" && match.groupValues[2] == "/price" -> {
            val price = exchange.requestBody.bufferedReader().readText().trim().toLongOrNull()
            require(price != null && price in 1..1_000_000)
            reply(exchange, 200, cache.update(id, price).encode())
        }
        else -> reply(exchange, 405, "method not allowed")
    }
}

private fun reply(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.write(bytes)
}
