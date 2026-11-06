package lab

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Relay : AutoCloseable {
    private var process: Process? = null

    @Synchronized
    fun start(eventId: String): String {
        if (process?.isAlive == true) return "running"
        process = ProcessBuilder(
            "java", "-Xms16m", "-Xmx96m", "-cp", System.getProperty("java.class.path"),
            "lab.MainKt", "relay", eventId,
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.INHERIT).start()
        return "started"
    }

    @Synchronized
    fun status(): String {
        val current = process ?: return "idle"
        return if (current.isAlive) "running" else "done:${current.exitValue()}"
    }

    @Synchronized
    override fun close() {
        process?.let {
            it.destroy()
            if (!it.waitFor(2, TimeUnit.SECONDS)) it.destroyForcibly()
        }
    }
}

fun HttpExchange.reply(status: Int, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

fun route(exchange: HttpExchange, relay: Relay, projection: Projection) {
    val methods = mapOf("/health" to "GET", "/orders" to "POST", "/state" to "GET",
        "/relay/start" to "POST", "/relay/status" to "GET", "/progress" to "GET")
    val path = exchange.requestURI.path
    val method = methods[path] ?: return exchange.reply(404, "missing")
    if (exchange.requestMethod != method) return exchange.reply(405, "method")
    val bytes = exchange.requestBody.use { it.readNBytes(1025) }
    if (bytes.size > 1024) return exchange.reply(400, "invalid")
    val body = bytes.toString(Charsets.UTF_8)
    when (path) {
        "/health" -> { Postgres().use { it.query("SELECT 1") }; exchange.reply(200, "ready") }
        "/orders" -> {
            val request = parseOrder(body) ?: return exchange.reply(400, "invalid")
            OrderWorkflow.create(request.first, request.second)
            exchange.reply(202, "accepted:${request.first.id}")
        }
        "/state" -> Postgres().use { db ->
            exchange.reply(200, db.query("SELECT order_id,amount FROM orders ORDER BY order_id")
                .joinToString("\n") { it.joinToString("|") })
        }
        "/relay/start" -> {
            if (body.isNotEmpty() && !body.matches(Regex("evt-[a-z][a-z0-9-]{0,29}"))) {
                return exchange.reply(400, "invalid")
            }
            exchange.reply(200, relay.start(body))
        }
        "/relay/status" -> exchange.reply(200, relay.status())
        "/progress" -> exchange.reply(200, projection.progress())
    }
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "relay") {
        Publisher(args.getOrElse(1) { "" }).use(OrderWorkflow::relay)
        return
    }
    initializeDatabase()
    initializeKafka()
    val relay = Relay()
    val projection = Projection()
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val port = System.getenv("PORT")?.toInt() ?: 8080
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
    server.executor = executor
    server.createContext("/") { exchange ->
        try {
            route(exchange, relay, projection)
        } catch (_: RollbackRequested) {
            exchange.reply(409, "rolled-back")
        } catch (_: ReplyLost) {
            exchange.reply(503, "uncertain")
        } catch (_: Exception) {
            exchange.reply(503, "unavailable")
        } finally {
            exchange.close()
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        relay.close()
        projection.close()
        executor.shutdownNow()
    })
    server.start()
}
