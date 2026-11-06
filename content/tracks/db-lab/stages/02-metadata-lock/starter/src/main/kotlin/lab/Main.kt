package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.sql.SQLException
import java.util.concurrent.Executors

fun main() {
    val database = Database()
    database.initialize()
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", (System.getenv("PORT") ?: "8080").toInt()), 0)
    server.executor = Executors.newVirtualThreadPerTaskExecutor()
    server.createContext("/") { exchange -> handle(exchange, database) }
    Runtime.getRuntime().addShutdownHook(Thread {
        database.release("rollback")
        server.stop(0)
    })
    server.start()
}

private fun handle(exchange: HttpExchange, database: Database) {
    try {
        val response = when (exchange.requestMethod to exchange.requestURI.path) {
            "GET" to "/health" -> 200 to "ready"
            "GET" to "/item" -> 200 to database.read().toString()
            "GET" to "/state" -> 200 to database.ddlState.get()
            "POST" to "/hold" -> { database.hold(); 200 to "held" }
            "POST" to "/ddl" -> { database.alter(); 202 to "started" }
            "POST" to "/release" -> release(exchange, database)
            else -> 404 to "missing"
        }
        reply(exchange, response.first, response.second)
    } catch (error: SQLException) {
        if (error.errorCode == 1205) reply(exchange, 503, "metadata-timeout")
        else reply(exchange, 500, "database-error")
    } catch (_: Exception) {
        reply(exchange, 409, "invalid-state")
    } finally {
        exchange.close()
    }
}

private fun release(exchange: HttpExchange, database: Database): Pair<Int, String> {
    val mode = exchange.requestURI.rawQuery?.removePrefix("mode=")
    if (mode != "commit" && mode != "rollback") return 400 to "invalid-mode"
    database.release(mode)
    return 200 to "released"
}

private fun reply(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}
