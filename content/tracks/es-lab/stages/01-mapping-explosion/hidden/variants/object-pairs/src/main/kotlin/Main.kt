import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

private val identifier = Regex("[a-z0-9-]{1,40}")
private val attributeKey = Regex("[a-z][a-z0-9_]{0,39}")

fun main() {
    val port = System.getenv("PORT").toInt()
    val index = System.getenv("ES_INDEX")
    require(identifier.matches(index))
    val catalog = Catalog(Elastic(System.getenv("ES_URL"), index))
    catalog.initialize()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    server.executor = executor
    server.createContext("/") { exchange -> handle(exchange, catalog) }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(0); executor.close() })
    server.start()
}

private fun handle(exchange: HttpExchange, catalog: Catalog) {
    exchange.use {
        try {
            val response = route(exchange, catalog)
            respond(exchange, response.status, response.body)
        } catch (error: ElasticFailure) {
            respond(exchange, 502, error.responseBody)
        } catch (_: IllegalArgumentException) {
            respond(exchange, 400, "{}")
        } catch (_: JsonError) {
            respond(exchange, 400, "{}")
        } catch (_: Exception) {
            respond(exchange, 502, "{}")
        }
    }
}

private data class Reply(val status: Int, val body: String = "{}")

private fun route(exchange: HttpExchange, catalog: Catalog): Reply {
    val path = exchange.requestURI.path
    return when {
        exchange.requestMethod == "GET" && path == "/health" -> Reply(200)
        exchange.requestMethod == "POST" && path == "/refresh" -> {
            catalog.refresh()
            Reply(200)
        }
        exchange.requestMethod == "PUT" && path.startsWith("/products/") -> {
            val id = path.removePrefix("/products/")
            require(identifier.matches(id))
            catalog.put(readProduct(id, readBody(exchange)))
            Reply(201)
        }
        exchange.requestMethod == "POST" && path == "/search" -> {
            val ids = catalog.search(readSearch(readBody(exchange)))
            Reply(200, jsonObject("ids" to JsonArray(ids.map(::jsonString))).toIndentedJson())
        }
        else -> Reply(404)
    }
}

private fun readBody(exchange: HttpExchange): JsonObject =
    parseObject(exchange.requestBody.bufferedReader(Charsets.UTF_8).readText())

private fun readProduct(id: String, body: JsonObject): Product {
    require(body.fields.keys == setOf("seller", "attributes"))
    val seller = body.string("seller")
    require(identifier.matches(seller))
    val attributes = body.objectValue("attributes").fields.mapValues { (key, value) ->
        require(attributeKey.matches(key))
        val text = (value as? JsonString)?.value ?: throw IllegalArgumentException("Expected string")
        require(text.length in 1..1024)
        text
    }
    require(attributes.size <= 8)
    return Product(id, seller, attributes)
}

private fun readSearch(body: JsonObject): Search {
    require(body.fields.keys.all { it in setOf("seller", "filters") })
    val seller = body.fields["seller"]?.let { body.string("seller") }
    require(seller == null || identifier.matches(seller))
    val filters = body.array("filters").map { value ->
        val filter = value as? JsonObject ?: throw IllegalArgumentException("Expected object")
        require(filter.fields.keys == setOf("key", "value"))
        val key = filter.string("key")
        val text = filter.string("value")
        require(attributeKey.matches(key) && text.length in 1..1024)
        AttributeFilter(key, text)
    }
    require(filters.size <= 3)
    return Search(seller, filters)
}

private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.write(bytes)
}
