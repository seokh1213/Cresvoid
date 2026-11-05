import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun handleBatch(exchange: HttpExchange) {
    if (exchange.requestURI.path != "/pairs" || exchange.requestMethod != "POST") {
        respond(exchange, 404, "not-found\n")
        return
    }
    val batch = try {
        val bytes = exchange.requestBody.use { it.readNBytes(1_048_577) }
        require(bytes.size <= 1_048_576)
        parseBatch(bytes.toString(Charsets.UTF_8))
    } catch (_: IllegalArgumentException) {
        respond(exchange, 400, "invalid-batch\n")
        return
    }
    val result = findMatches(batch)
    val body = buildString {
        append("COMPARISONS ").append(result.comparisons).append('\n')
        for (pair in result.pairs) append(pair.left).append(' ').append(pair.right).append('\n')
    }
    respond(exchange, 200, body)
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    require(port in 1..65535)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 64)
    server.executor = executor
    server.createContext("/health") { exchange ->
        exchange.use {
            if (it.requestURI.path == "/health" && it.requestMethod == "GET") respond(it, 200, "ready\n")
            else respond(it, 404, "not-found\n")
        }
    }
    server.createContext("/pairs") { exchange -> exchange.use(::handleBatch) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}
