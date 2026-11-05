import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

fun route(exchange: HttpExchange, catalog: ElasticCatalog) {
    try {
        val path = exchange.requestURI.path
        val method = exchange.requestMethod
        if (method == "GET" && path == "/health") {
            respond(exchange, 200, "{\"ready\":true}")
            return
        }
        if (method != "POST" || path !in setOf("/catalog", "/analyze", "/search")) {
            respond(exchange, 404, "{}")
            return
        }
        val body = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
        require(body.length in 1..32_768)
        val result = when (path) {
            "/catalog" -> catalog.replaceProducts(body)
            "/search" -> catalog.search(body)
            else -> catalog.analyze(exchange.requestURI.rawQuery?.removePrefix("profile=") ?: "catalog", body)
        }
        respond(exchange, 200, result)
    } catch (_: IllegalArgumentException) {
        respond(exchange, 400, "{\"error\":\"invalid request\"}")
    } catch (failure: Exception) {
        System.err.println(failure.message)
        respond(exchange, 502, "{\"error\":\"search unavailable\"}")
    } finally {
        exchange.close()
    }
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val catalog = ElasticCatalog(requireNotNull(System.getenv("ES_URL")))
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = executor
    server.createContext("/") { route(it, catalog) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
        catalog.close()
    })
    server.start()
    CountDownLatch(1).await()
}
