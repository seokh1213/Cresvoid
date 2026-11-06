package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.sql.SQLException
import java.util.concurrent.Executors

fun main() {
    val database = Database()
    val port = System.getenv("PORT")?.toInt() ?: 8080
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.executor = Executors.newVirtualThreadPerTaskExecutor()
    server.createContext("/") { exchange ->
        try {
            route(exchange, database)
        } catch (_: IllegalArgumentException) {
            exchange.reply(400, "invalid")
        } catch (_: Exception) {
            exchange.reply(500, "internal")
        } finally {
            exchange.close()
        }
    }
    server.start()
}

private fun route(exchange: HttpExchange, database: Database) {
    val parameters = exchange.requestURI.rawQuery.orEmpty().split('&')
        .filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
    when (exchange.requestURI.path) {
        "/health" -> database.connect().use { exchange.reply(200, "ready") }
        "/prepare" -> {
            require(exchange.requestMethod == "POST")
            database.prepare(parameters.getValue("rounds").toInt().also { require(it in 1..100) })
            exchange.reply(200, "ready")
        }
        "/leave" -> {
            require(exchange.requestMethod == "POST")
            val request = LeaveRequest(
                parameters.getValue("round").toInt().also { require(it in 1..100) },
                parameters.getValue("doctor").toInt().also { require(it in 1..2) },
                parameters.getValue("maxAttempts").toInt().also { require(it in 1..5) },
                parameters["faults"]?.toInt()?.also { require(it in 0..99) } ?: 0,
                parameters["state"] ?: "40001",
            )
            val trace = RetryTrace()
            try {
                val result = retry(request.limit, trace) {
                    database.leave(request, trace, parameters["paired"] == "true")
                }
                exchange.reply(200, "outcome=$result;${trace.fields()}")
            } catch (failure: SQLException) {
                val status = if (failure.sqlState == "40001") 409 else 422
                exchange.reply(status, "state=${failure.sqlState};${trace.fields()}")
            }
        }
        else -> exchange.reply(404, "missing")
    }
}

private fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.write(bytes)
}
