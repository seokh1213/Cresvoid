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
    val endpoint = requireNotNull(System.getenv("ES_URL"))
    val index = requireNotNull(System.getenv("ES_INDEX"))
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/health") { exchange -> exchange.reply(200, "{}") }
    server.createContext("/brands") { exchange ->
        try {
            require(exchange.requestMethod == "GET")
            val parameters = exchange.requestURI.rawQuery.orEmpty().split('&')
                .filter { it.isNotEmpty() }.associate { entry ->
                    val parts = entry.split('=', limit = 2)
                    require(parts.size == 2)
                    parts[0] to parts[1]
                }
            require(parameters.keys.all { it == "size" || it == "shard_size" })
            val size = parameters["size"]?.toInt() ?: 10
            val shardSize = parameters["shard_size"]?.toInt()
            require(size in 1..100 && (shardSize == null || shardSize in 1..256))
            val request = HttpRequest.newBuilder(URI.create("$endpoint/$index/_search"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(brandQuery(size, shardSize))).build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            exchange.reply(response.statusCode(), reportBody(response.body()))
        } catch (_: IllegalArgumentException) {
            exchange.reply(400, "{\"error\":\"invalid query\"}")
        } catch (_: Exception) {
            exchange.reply(502, "{\"error\":\"upstream unavailable\"}")
        }
    }
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(0)
        executor.close()
        client.close()
    })
    server.start()
}

private fun HttpExchange.reply(status: Int, body: String) {
    responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    val bytes = body.toByteArray(Charsets.UTF_8)
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
    close()
}
