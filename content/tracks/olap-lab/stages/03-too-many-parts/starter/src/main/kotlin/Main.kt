import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

fun parseEvents(body: String): List<Event> {
    val lines = body.removeSuffix("\n").split('\n')
    require(lines.size in 1..2)
    return lines.map { line ->
        val fields = line.split('\t')
        require(fields.size == 2)
        val id = fields[0].toLongOrNull()
        val value = fields[1].toLongOrNull()
        require(id != null && id > 0 && value != null && value > 0)
        Event(id, value)
    }
}

fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

fun handle(exchange: HttpExchange, ingestor: Ingestor) {
    exchange.use {
        try {
            when (exchange.requestMethod to exchange.requestURI.path) {
                "GET" to "/health" -> respond(exchange, 200, "ready")
                "POST" to "/events" -> {
                    val events = parseEvents(exchange.requestBody.bufferedReader(Charsets.UTF_8).readText())
                    ingestor.accept(events)
                    respond(exchange, 202, "accepted")
                }
                "POST" to "/flush" -> {
                    require(exchange.requestBody.read() == -1)
                    ingestor.flush()
                    respond(exchange, 200, "flushed")
                }
                else -> respond(exchange, 404, "not found")
            }
        } catch (_: IllegalArgumentException) {
            respond(exchange, 400, "invalid events")
        } catch (error: InsertRejected) {
            exchange.responseHeaders.set("X-Storage-Http-Status", error.httpStatus.toString())
            exchange.responseHeaders.set("X-Storage-Error-Code", error.databaseCode.toString())
            respond(exchange, 503, "storage unavailable")
        } catch (_: Exception) {
            respond(exchange, 503, "storage unavailable")
        }
    }
}

fun main() {
    val clickHouse = ClickHouse(
        requireNotNull(System.getenv("CLICKHOUSE_URL")),
        requireNotNull(System.getenv("CLICKHOUSE_TABLE")),
    )
    val ingestor = Ingestor(clickHouse)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", requireNotNull(System.getenv("PORT")).toInt()), 128)
    server.executor = executor
    server.createContext("/") { handle(it, ingestor) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}
