import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object WorkDispatchers {
    val fast = Dispatchers.Default.limitedParallelism(2)
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: error("PORT must be a valid local TCP port")
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    val blockingCall = BlockingCall()
    server.executor = Executors.newCachedThreadPool()
    server.createContext("/") { exchange ->
        exchange.use {
            when (exchange.requestURI.path) {
                "/health" -> exchange.respond(200, "ok")
                "/fast" -> {
                    runBlocking { withContext(WorkDispatchers.fast) { Unit } }
                    exchange.respond(200, "fast")
                }
                "/blocking" -> {
                    runBlocking { blockingCall.execute() }
                    exchange.respond(200, "blocked")
                }
                "/internal/stats" -> exchange.respond(200, blockingCall.stats())
                else -> exchange.respond(404, "not found")
            }
        }
    }
    server.start()
}

private fun HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.write(bytes)
}
