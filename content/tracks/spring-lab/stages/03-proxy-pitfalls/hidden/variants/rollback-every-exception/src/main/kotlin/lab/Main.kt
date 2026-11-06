package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

private val cases = setOf("self", "private", "final", "checked", "required", "nested")

private fun HttpExchange.reply(status: Int, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun observe(case: String): String = Database().use { database ->
    val exception = recordException { executeCase(case, database) }
    val counts = database.counts()
    "case=$case\nrows=${counts.total}\nexception=$exception\n" +
        "outer_rows=${counts.outer}\ninner_rows=${counts.inner}\ntransactional_rows=${counts.transactional}\n"
}

private fun handle(exchange: HttpExchange) {
    exchange.use {
        val path = exchange.requestURI.path
        val case = path.removePrefix("/observe/")
        when {
            path == "/health" && exchange.requestMethod == "GET" -> exchange.reply(200, "ready\n")
            path == "/health" -> exchange.reply(405, "method\n")
            path.startsWith("/observe/") && case in cases && exchange.requestMethod == "POST" ->
                exchange.reply(200, observe(case))
            path.startsWith("/observe/") && case in cases -> exchange.reply(405, "method\n")
            else -> exchange.reply(404, "path\n")
        }
    }
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0).apply {
        this.executor = executor
        createContext("/") { exchange -> handle(exchange) }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
    })
    server.start()
}
