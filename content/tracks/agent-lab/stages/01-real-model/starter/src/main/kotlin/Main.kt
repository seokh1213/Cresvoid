import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.system.exitProcess

fun main() {
    val key = System.getenv("OPENCODE_API_KEY")?.takeIf { it.isNotBlank() }
    if (key == null) {
        System.err.println("OPENCODE_API_KEY is required")
        exitProcess(2)
    }
    val agent = Agent(ModelClient(key))
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", System.getenv("PORT").toInt()), 0)
    server.executor = executor
    server.createContext("/health") { exchange -> exchange.reply(200, "{}") }
    server.createContext("/run") { exchange -> handleRun(exchange, agent) }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(0); executor.close() })
    server.start()
}

private fun handleRun(exchange: HttpExchange, agent: Agent) {
    if (exchange.requestMethod != "POST" || exchange.requestURI.path != "/run") {
        exchange.reply(405, "{}"); return
    }
    val settings = runCatching {
        val bytes = exchange.requestBody.readNBytes(8193)
        require(bytes.size <= 8192)
        val input = parseJson(bytes.toString(Charsets.UTF_8))
        val limit = input["turn_limit"].integer()
        require(limit in 1..8)
        val prompt = input["prompt"].string()
        val description = input["description"].string()
        val cache = input["cache"].string()
        require(prompt.length in 1..2000 && description in setOf("brief", "detailed") && cache in setOf("cold", "stable"))
        RunSettings(prompt, description, cache, limit.toInt())
    }.getOrNull()
    if (settings == null) { exchange.reply(400, "{}"); return }
    try {
        exchange.reply(200, agent.run(settings).toJson())
    } catch (_: BudgetExceeded) {
        exchange.reply(429, "{\"error\":\"budget\"}")
    } catch (_: Exception) {
        exchange.reply(502, "{\"error\":\"model_unavailable\"}")
    }
}

private fun HttpExchange.reply(status: Int, body: String) {
    responseHeaders.set("Content-Type", "application/json")
    val bytes = body.toByteArray(Charsets.UTF_8)
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}
