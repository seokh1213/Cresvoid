package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.Security
import java.util.concurrent.Executors

fun main() {
    // Docker can assign a different IP when a stopped broker rejoins the network.
    Security.setProperty("networkaddress.cache.ttl", "0")
    Security.setProperty("networkaddress.cache.negative.ttl", "0")
    val journal = BrokerJournal(System.getenv("KAFKA_BOOTSTRAP") ?: "kafka1:9092,kafka2:9092,kafka3:9092")
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", (System.getenv("PORT") ?: "8080").toInt()), 0)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/") { exchange ->
        exchange.use {
            try {
                route(exchange, journal)
            } catch (error: IllegalArgumentException) {
                reply(exchange, 400, "invalid request")
            } catch (error: Exception) {
                System.err.println("request ${exchange.requestURI.path}: ${error.javaClass.simpleName}: ${error.message}")
                reply(exchange, 500, "request failed")
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
        journal.close()
    })
    server.start()
}

private fun route(exchange: HttpExchange, journal: BrokerJournal) {
    val path = exchange.requestURI.path
    val method = exchange.requestMethod
    when {
        method == "GET" && path == "/health" -> reply(exchange, 200, "ready")
        method == "POST" && path == "/setup" -> {
            journal.setup()
            reply(exchange, 200, "configured")
        }
        method == "GET" && path == "/topology" -> reply(exchange, 200, journal.topology())
        method == "GET" && path == "/receipts" -> reply(exchange, 200, journal.receipts())
        method == "GET" && path.startsWith("/records/") -> {
            val name = path.removePrefix("/records/")
            require(journal.contains(name))
            reply(exchange, 200, journal.records(name))
        }
        method == "POST" && path.startsWith("/send/") -> {
            val fields = path.removePrefix("/send/").split('/')
            require(fields.size == 2 && journal.contains(fields[0]) && fields[1].matches(Regex("[bd][0-3]")))
            val status = journal.send(fields[0], fields[1])
            val code = when (status) { "ACK" -> 200; "NOT_ENOUGH_REPLICAS" -> 409; else -> 500 }
            reply(exchange, code, "$status ${fields[1]}")
        }
        else -> reply(exchange, 404, "not found")
    }
}

private fun reply(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.write(bytes)
}
