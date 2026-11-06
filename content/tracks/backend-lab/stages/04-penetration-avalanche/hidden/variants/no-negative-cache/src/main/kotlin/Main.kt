import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

private data class Reply(val status: Int, val body: String)

private fun route(exchange: HttpExchange, policy: CachePolicy, origin: Origin, seed: Seed): Reply {
    val path = exchange.requestURI.path
    val method = exchange.requestMethod
    if (method == "GET" && path == "/health") return Reply(200, "{}")
    if (method == "POST" && path == "/admin/seed") return Reply(200, seed.fill())
    if (method == "GET" && path == "/admin/stats") return Reply(200, origin.snapshot().json())
    if (method == "PUT" && path.startsWith("/admin/products/")) {
        val id = path.substringAfterLast('/').toIntOrNull() ?: return Reply(400, "{}")
        if (id !in 1..20_000) return Reply(400, "{}")
        origin.create(id)
        return Reply(204, "")
    }
    if (method == "GET" && path.startsWith("/products/")) {
        val id = path.removePrefix("/products/").toIntOrNull() ?: return Reply(400, "{}")
        if (id !in 1..20_000) return Reply(400, "{}")
        val value = policy.get(id)
        return if (value == null) Reply(404, "{\"error\":\"not_found\"}") else Reply(200, value)
    }
    return Reply(404, "{}")
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val redis = RedisClient("127.0.0.1", requireNotNull(System.getenv("REDIS_PORT")).toInt())
    check(redis.call("PING") == "PONG")
    val origin = Origin()
    val policy = CachePolicy(redis, origin)
    val seed = Seed(redis, policy)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 1024)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/") { exchange ->
        exchange.use {
            val reply = try {
                route(exchange, policy, origin, seed)
            } catch (_: OriginOverloaded) {
                Reply(503, "{\"error\":\"origin_overloaded\"}")
            } catch (_: Exception) {
                Reply(500, "{\"error\":\"internal\"}")
            }
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            val bytes = reply.body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(reply.status, if (reply.status == 204) -1 else bytes.size.toLong())
            if (reply.status != 204) exchange.responseBody.write(bytes)
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
        redis.close()
    })
    server.start()
}
