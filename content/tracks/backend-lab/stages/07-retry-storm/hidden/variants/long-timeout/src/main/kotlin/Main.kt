import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

data class Reply(val status: Int, val body: String)

fun respond(exchange: HttpExchange, reply: Reply) {
    exchange.use {
        val bytes = reply.body.toByteArray(Charsets.UTF_8)
        it.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        if (reply.status == 503) it.responseHeaders.set("Retry-After", "1")
        it.sendResponseHeaders(reply.status, bytes.size.toLong())
        it.responseBody.write(bytes)
    }
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val downstream = DownstreamClient(requireNotNull(System.getenv("DOWNSTREAM_URL")))
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = executor
    server.createContext("/") { exchange ->
        val path = exchange.requestURI.path
        val reply = when {
            exchange.requestMethod != "GET" -> Reply(405, "{\"error\":\"method\"}")
            path == "/health" -> Reply(200, "{\"ready\":true}")
            path.matches(Regex("/quotes/[a-z0-9-]{1,64}")) -> {
                try {
                    downstream.fetch(path.substringAfterLast('/'))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Reply(503, "{\"error\":\"unavailable\"}")
                }
            }
            else -> Reply(404, "{\"error\":\"missing\"}")
        }
        respond(exchange, reply)
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}
