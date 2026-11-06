import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

fun main() {
    val redis = RedisClient(
        System.getenv("REDIS_HOST") ?: "127.0.0.1",
        requireNotNull(System.getenv("REDIS_PORT")).toInt(),
    )
    val limiter = RateLimiter(redis, requireNotNull(System.getenv("REDIS_PREFIX")))
    val instance = requireNotNull(System.getenv("INSTANCE_ID"))
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", requireNotNull(System.getenv("PORT")).toInt()), 256)
    server.executor = executor
    server.createContext("/") { exchange ->
        exchange.use {
            try {
                route(exchange, redis, limiter, instance)
            } catch (_: Exception) {
                respond(exchange, 503, "unavailable")
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
        redis.close()
    })
    server.start()
    CountDownLatch(1).await()
}

private fun route(exchange: HttpExchange, redis: RedisClient, limiter: RateLimiter, instance: String) {
    when (exchange.requestURI.path) {
        "/health" -> {
            if (exchange.requestMethod != "GET") return respond(exchange, 405, "method")
            check(redis.call("PING") == "PONG")
            respond(exchange, 200, "ok")
        }
        "/requests" -> {
            if (exchange.requestMethod != "POST") return respond(exchange, 405, "method")
            val user = exchange.requestHeaders.getFirst("X-User")
            if (user == null || !user.matches(Regex("[a-z0-9-]{1,40}"))) {
                return respond(exchange, 400, "user")
            }
            val decision = limiter.allow(user)
            if (!decision.allowed) exchange.responseHeaders.set("Retry-After", "1")
            exchange.responseHeaders.set("Content-Type", "application/json")
            respond(exchange, if (decision.allowed) 200 else 429,
                """{"allowed":${decision.allowed},"decision_us":${decision.atMicros},"instance":"$instance"}""")
        }
        else -> respond(exchange, 404, "missing")
    }
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.write(bytes)
}
