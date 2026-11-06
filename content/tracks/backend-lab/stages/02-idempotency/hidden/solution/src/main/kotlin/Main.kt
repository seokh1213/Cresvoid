import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

private val identifier = Regex("[A-Za-z0-9_-]{1,64}")
private val jsonWhitespace = """[ \t\r\n]*"""
private val encodedIdentifier = """"(?:[A-Za-z0-9_-]|\\u[0-9A-Fa-f]{4}){1,64}""""
private val amountToken = Regex("[1-9][0-9]{0,6}")
private val paymentField = "($encodedIdentifier)$jsonWhitespace:$jsonWhitespace($encodedIdentifier|${amountToken.pattern})"
private val paymentObject = Regex("""$jsonWhitespace\{$jsonWhitespace$paymentField$jsonWhitespace,$jsonWhitespace$paymentField$jsonWhitespace}$jsonWhitespace""")
private val unicodeEscape = Regex("""\\u([0-9A-Fa-f]{4})""")

private fun decodeIdentifier(token: String): String? {
    if (!token.startsWith('"') || !token.endsWith('"')) return null
    val decoded = unicodeEscape.replace(token.substring(1, token.length - 1)) {
        it.groupValues[1].toInt(16).toChar().toString()
    }
    return decoded.takeIf { identifier.matches(it) }
}

private fun parsePayment(body: String): PaymentRequest? {
    val match = paymentObject.matchEntire(body) ?: return null
    val firstName = decodeIdentifier(match.groupValues[1]) ?: return null
    val secondName = decodeIdentifier(match.groupValues[3]) ?: return null
    val fields = mapOf(firstName to match.groupValues[2], secondName to match.groupValues[4])
    if (fields.keys != setOf("orderId", "amount")) return null
    val orderId = decodeIdentifier(fields.getValue("orderId")) ?: return null
    val amount = fields.getValue("amount")
    if (!amountToken.matches(amount)) return null
    return PaymentRequest(orderId, amount.toInt())
}

private fun payments(exchange: HttpExchange, store: IdempotencyStore): PaymentResponse {
    val key = exchange.requestHeaders.getFirst("Idempotency-Key")
        ?.takeIf { identifier.matches(it) }
        ?: return PaymentResponse(400, """{"error":"invalid_request"}""")
    val bytes = exchange.requestBody.readNBytes(1025)
    if (bytes.size > 1024) return PaymentResponse(400, """{"error":"invalid_request"}""")
    val request = parsePayment(bytes.toString(Charsets.UTF_8))
        ?: return PaymentResponse(400, """{"error":"invalid_request"}""")
    return store.execute(key, request)
}

private fun route(exchange: HttpExchange, store: IdempotencyStore, gateway: PaymentGateway): PaymentResponse =
    when {
        exchange.requestMethod == "GET" && exchange.requestURI.path == "/health" ->
            PaymentResponse(200, """{"status":"ready"}""")
        exchange.requestMethod == "GET" && exchange.requestURI.path == "/gate" ->
            PaymentResponse(200, """{"held":${gateway.isHeld()}}""")
        exchange.requestMethod == "POST" && exchange.requestURI.path == "/gate/release" -> {
            gateway.releaseHeld()
            PaymentResponse(200, """{"released":true}""")
        }
        exchange.requestMethod == "GET" && exchange.requestURI.path == "/stats" -> {
            val key = exchange.requestURI.rawQuery?.removePrefix("key=")
            if (key == null || !identifier.matches(key)) PaymentResponse(400, """{"error":"invalid_request"}""")
            else PaymentResponse(200, """{"calls":${gateway.callCount(key)}}""")
        }
        exchange.requestMethod == "POST" && exchange.requestURI.path == "/payments" -> payments(exchange, store)
        else -> PaymentResponse(404, """{"error":"not_found"}""")
    }

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: error("PORT is required")
    val gateway = PaymentGateway()
    val store = IdempotencyStore(gateway)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = executor
    server.createContext("/") { exchange ->
        exchange.use {
            val response = try {
                route(exchange, store, gateway)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                PaymentResponse(500, """{"error":"interrupted"}""")
            } catch (_: Exception) {
                PaymentResponse(500, """{"error":"internal"}""")
            }
            val bytes = response.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(response.status, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
    })
    server.start()
}
