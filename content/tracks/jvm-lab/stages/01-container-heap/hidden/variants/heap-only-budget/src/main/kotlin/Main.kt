import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    require(port in 1..65535)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    val executor = Executors.newFixedThreadPool(2)
    server.executor = executor
    server.createContext("/") { exchange -> handle(exchange) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}

private fun handle(exchange: HttpExchange) {
    exchange.use {
        if (exchange.requestMethod != "GET") {
            respond(exchange, 405, "method")
            return
        }
        when (exchange.requestURI.path) {
            "/health" -> respond(exchange, 200, "ready")
            "/probe" -> {
                val request = try {
                    parseRequest(exchange.requestURI.rawQuery)
                } catch (_: IllegalArgumentException) {
                    respond(exchange, 400, "parameters")
                    return
                }
                respond(exchange, 200, measure(request))
            }
            else -> respond(exchange, 404, "path")
        }
    }
}

private fun parseRequest(query: String?): ProbeRequest {
    val entries = requireNotNull(query).split('&').map { part ->
        val pieces = part.split('=')
        require(pieces.size == 2)
        pieces[0] to pieces[1].toInt()
    }
    require(entries.size == 3)
    val values = entries.toMap()
    require(values.keys == setOf("heapMiB", "directMiB", "budgetMiB"))
    val heap = values.getValue("heapMiB")
    val direct = values.getValue("directMiB")
    val budget = values.getValue("budgetMiB")
    require(heap in 0..768 && direct in 0..384 && budget in 256..1024)
    return ProbeRequest(heap * MIB, direct * MIB, budget * MIB)
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = "$body\n".toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.write(bytes)
}
