import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors

fun main() {
    val port = requireNotNull(System.getenv("PORT")).toInt()
    val downstream = DownstreamClient(requireNotNull(System.getenv("DOWNSTREAM_URL")))
    val limiter = Limiter()
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 256)
    server.executor = executor
    server.createContext("/") { exchange ->
        exchange.use {
            when {
                exchange.requestMethod == "GET" && exchange.requestURI.path == "/health" ->
                    exchange.reply(200, "ready")
                exchange.requestMethod == "POST" && exchange.requestURI.path == "/work" ->
                    handleWork(exchange, limiter, downstream)
                else -> exchange.reply(404, "missing")
            }
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.shutdownNow()
    })
    server.start()
}

private fun handleWork(exchange: HttpExchange, limiter: Limiter, downstream: DownstreamClient) {
    val requestId = exchange.requestBody.bufferedReader().use { it.readText() }
    if (!requestId.matches(Regex("[a-zA-Z0-9-]{1,80}"))) {
        exchange.reply(400, "invalid")
        return
    }
    if (!limiter.tryAcquire()) {
        exchange.responseHeaders.set("Retry-After", "1")
        exchange.reply(429, "busy")
        return
    }
    val began = System.nanoTime()
    val result = try {
        downstream.execute(requestId)
    } catch (_: Exception) {
        null
    } finally {
        limiter.onComplete(System.nanoTime() - began)
    }
    if (result == null) exchange.reply(502, "unavailable") else exchange.reply(200, result)
}

private fun HttpExchange.reply(status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private class DownstreamClient(base: String) {
    private val endpoint = URI.create("$base/execute")
    private val client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    fun execute(requestId: String): String {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(requestId))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200)
        check(response.body() == "done:$requestId")
        return response.body()
    }
}
