import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class OrderRequest(val productId: String, val quantity: Int)

fun parseOrder(body: String): OrderRequest? {
    val match = OrderJson.objectPattern.matchEntire(body) ?: return null
    val firstKey = OrderJson.decodeString(match.groupValues[1])
    val secondKey = OrderJson.decodeString(match.groupValues[3])
    val fields = mapOf(firstKey to match.groupValues[2], secondKey to match.groupValues[4])
    if (fields.keys != setOf("productId", "quantity")) return null
    val productValue = fields.getValue("productId")
    if (!productValue.startsWith('"')) return null
    val productId = OrderJson.decodeString(productValue)
    if (!productId.matches(Regex("[a-zA-Z0-9-]+"))) return null
    val count = fields.getValue("quantity").toIntOrNull() ?: return null
    return OrderRequest(productId, count).takeIf { count in 1..10_000 }
}

private object OrderJson {
    private const val SPACE = "[ \\t\\r\\n]*"
    private const val STRING = "\"(?:[^\"\\\\\\x00-\\x1f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*+\""
    private const val VALUE = "(?:$STRING|-?(?:0|[1-9][0-9]*))"
    val objectPattern = Regex("$SPACE\\{$SPACE($STRING)$SPACE:$SPACE($VALUE)$SPACE,$SPACE($STRING)$SPACE:$SPACE($VALUE)$SPACE}$SPACE")
    private val escape = Regex("""\\(?:u([0-9a-fA-F]{4})|(["\\/bfnrt]))""")

    fun decodeString(value: String): String = escape.replace(value.substring(1, value.length - 1)) {
        if (it.groupValues[1].isNotEmpty()) it.groupValues[1].toInt(16).toChar().toString()
        else when (it.groupValues[2]) {
            "b" -> "\b"
            "f" -> "\u000c"
            "n" -> "\n"
            "r" -> "\r"
            "t" -> "\t"
            else -> it.groupValues[2]
        }
    }
}

class OrderHttpApi(private val repository: StockRepository) {
    private val services = listOf(OrderService(repository), OrderService(repository))
    private val nextService = AtomicInteger()

    fun handle(exchange: HttpExchange) {
        exchange.use {
            try {
                dispatch(exchange)
            } catch (_: Exception) {
                respond(exchange, 500, """{"error":"internal"}""")
            }
        }
    }

    private fun dispatch(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        when {
            exchange.requestMethod == "GET" && path == "/health" ->
                respond(exchange, 200, """{"status":"ready"}""")
            exchange.requestMethod == "GET" && path.startsWith("/stock/") ->
                stock(exchange, path.removePrefix("/stock/"))
            exchange.requestMethod == "POST" && path == "/orders" ->
                order(exchange)
            else -> respond(exchange, 404, """{"error":"not-found"}""")
        }
    }

    private fun stock(exchange: HttpExchange, productId: String) {
        val stock = repository.find(productId)
        if (stock == null) respond(exchange, 404, """{"error":"not-found"}""")
        else respond(exchange, 200, """{"productId":"$productId","remaining":${stock.remaining}}""")
    }

    private fun order(exchange: HttpExchange) {
        val bytes = exchange.requestBody.readNBytes(4097)
        val request = if (bytes.size <= 4096) parseOrder(bytes.toString(Charsets.UTF_8)) else null
        if (request == null) {
            respond(exchange, 400, """{"error":"invalid-order"}""")
            return
        }
        if (repository.find(request.productId) == null) {
            respond(exchange, 404, """{"error":"not-found"}""")
            return
        }
        val service = services[Math.floorMod(nextService.getAndIncrement(), services.size)]
        if (service.place(request.productId, request.quantity)) {
            respond(exchange, 201, """{"status":"accepted"}""")
        } else {
            respond(exchange, 409, """{"error":"insufficient-stock"}""")
        }
    }
}

fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.write(bytes)
}

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 256)
    server.executor = executor
    server.createContext("/") { exchange -> OrderHttpApiHolder.api.handle(exchange) }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}

object OrderHttpApiHolder {
    val api = OrderHttpApi(StockRepository())
}
