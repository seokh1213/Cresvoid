package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors

fun connection(): Connection = DriverManager.getConnection(
    System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"),
)

fun HttpExchange.respond(status: Int, text: String) {
    val bytes = text.toByteArray(UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

fun parameters(query: String?): Map<String, String> = query.orEmpty().split('&')
    .filter { it.contains('=') }
    .associate { part ->
        val (key, value) = part.split('=', limit = 2)
        URLDecoder.decode(key, UTF_8) to URLDecoder.decode(value, UTF_8)
    }

fun main() {
    connection().use { Queries.prepare(it) }
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", System.getenv("PORT").toInt()), 0)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/health") { exchange -> exchange.respond(200, "ready") }
    server.createContext("/query") { exchange ->
        try {
            require(exchange.requestMethod == "GET")
            val input = parameters(exchange.requestURI.rawQuery)
            val token = input.getValue("token")
            require(token.matches(Regex("r[0-9]+")))
            connection().use { db ->
                val query = Queries.select(input)
                db.prepareStatement("/*lab03:$token*/ ${query.sql}").use { statement ->
                    query.bind(statement)
                    statement.queryTimeout = 45
                    val ids = statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(rows.getLong(1)) }
                    }
                    exchange.respond(200, ids.joinToString(","))
                }
            }
        } catch (_: IllegalArgumentException) {
            exchange.respond(400, "invalid request")
        } catch (_: Exception) {
            exchange.respond(500, "query failed")
        } finally {
            exchange.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
    })
    server.start()
}
