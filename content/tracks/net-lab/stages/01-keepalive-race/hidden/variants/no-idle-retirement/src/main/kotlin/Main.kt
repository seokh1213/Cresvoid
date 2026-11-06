import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private fun respond(exchange: HttpExchange, reply: RelayReply) {
    val bytes = reply.body.toByteArray(Charsets.UTF_8)
    exchange.sendResponseHeaders(reply.status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun relay(exchange: HttpExchange, pools: ConcurrentHashMap<String, ConnectionPool>, port: Int) {
    val query = exchange.requestURI.rawQuery.orEmpty().split('&').associate {
        URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
            URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
    }
    val lane = query["lane"].orEmpty()
    val id = query["id"].orEmpty()
    val fault = query["fault"] ?: "none"
    if (exchange.requestMethod !in setOf("GET", "POST") ||
        !lane.matches(Regex("[a-z0-9-]{1,40}")) || !id.matches(Regex("[a-z0-9-]{1,40}")) ||
        fault !in setOf("none", "once", "always")
    ) {
        respond(exchange, RelayReply(400, "invalid-request"))
        return
    }
    exchange.requestBody.use { it.readAllBytes() }
    val pool = pools.computeIfAbsent(lane) { ConnectionPool(port) }
    val target = "/resource?id=$id&fault=$fault"
    respond(exchange, pool.execute(exchange.requestMethod, target))
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val upstreamPort = requireNotNull(System.getenv("UPSTREAM_PORT")).toInt()
    val pools = ConcurrentHashMap<String, ConnectionPool>()
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = executor
    server.createContext("/health") { exchange ->
        exchange.use { respond(it, RelayReply(200, "ready")) }
    }
    server.createContext("/relay") { exchange ->
        exchange.use { relay(it, pools, upstreamPort) }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        pools.values.forEach { it.close() }
        executor.shutdownNow()
    })
    server.start()
}
