import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val clickHouse = ClickHouse(requireNotNull(System.getenv("CLICKHOUSE_URL")))
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/") { exchange -> handle(exchange, clickHouse) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}

private fun handle(exchange: HttpExchange, clickHouse: ClickHouse) {
    try {
        when (exchange.requestURI.path) {
            "/health" -> respond(exchange, 200, "ready\n")
            "/summary" -> {
                val parameters = exchange.requestURI.rawQuery.orEmpty().split('&').associate {
                    URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
                        URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
                }
                val tenant = parameters["tenant"]?.toLongOrNull()
                val state = parameters["state"]
                require(tenant != null && tenant in 1..4294967295L)
                require(state in setOf("all", "active", "hold"))
                val sql = summaryQuery(tenant, requireNotNull(state))
                respond(exchange, 200, clickHouse.query(sql).trimEnd() + "\n")
            }
            else -> respond(exchange, 404, "unknown path\n")
        }
    } catch (_: IllegalArgumentException) {
        respond(exchange, 400, "invalid query\n")
    } catch (_: Exception) {
        respond(exchange, 500, "query failed\n")
    } finally {
        exchange.close()
    }
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.write(bytes)
}
