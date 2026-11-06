package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", (System.getenv("PORT") ?: "8080").toInt()), 32)
    server.executor = Executors.newVirtualThreadPerTaskExecutor()
    server.createContext("/") { exchange -> serve(exchange) }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(0) })
    server.start()
}

private fun serve(exchange: HttpExchange) {
    try {
        when {
            exchange.requestMethod == "GET" && exchange.requestURI.path == "/health" -> {
                connect().use { connection -> connection.createStatement().use { it.execute("SELECT 1") } }
                respond(exchange, 200, "ready\n")
            }
            exchange.requestMethod == "POST" && exchange.requestURI.path == "/observe" ->
                respond(exchange, 200, runPair(PairRequest(0, 20, 21), RunMode.OBSERVE).encode())
            exchange.requestMethod == "POST" && exchange.requestURI.path == "/pair" -> {
                val request = parseRequest(exchange.requestURI.rawQuery.orEmpty())
                respond(exchange, 200, runPair(request, RunMode.IMPLEMENT).encode())
            }
            else -> respond(exchange, 404, "unknown route\n")
        }
    } catch (_: IllegalArgumentException) {
        respond(exchange, 400, "invalid request\n")
    } catch (_: Exception) {
        respond(exchange, 500, "operation failed\n")
    } finally {
        exchange.close()
    }
}

private fun parseRequest(query: String): PairRequest {
    val fields = query.split('&').associate { part ->
        val pair = part.split('=', limit = 2)
        require(pair.size == 2)
        pair[0] to pair[1].toInt()
    }
    val batch = requireNotNull(fields["batch"])
    val left = requireNotNull(fields["left"])
    val right = requireNotNull(fields["right"])
    require(fields.keys == setOf("batch", "left", "right"))
    require(batch > 0 && left in 11..29 && right in 11..29)
    return PairRequest(batch, left, right)
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
