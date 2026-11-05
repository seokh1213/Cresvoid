import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class Elastic(private val base: String, val index: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun request(method: String, path: String, body: String = ""): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    fun requireSuccess(method: String, path: String, body: String = ""): String {
        val response = request(method, path, body)
        if (response.statusCode() !in 200..299) {
            throw ElasticFailure(response.statusCode(), response.body())
        }
        return response.body()
    }
}

class ElasticFailure(val status: Int, val responseBody: String) :
    RuntimeException("Elasticsearch request failed: $status $responseBody")

fun jsonObject(vararg fields: Pair<String, JsonValue>): JsonObject = JsonObject(linkedMapOf(*fields))
fun jsonString(value: String): JsonString = JsonString(value)
fun parseObject(text: String): JsonObject {
    val parsed = parseJson(text, KeyRule.ANY)
    require(!parsed.hasTrailingContent)
    return parsed.value as? JsonObject ?: throw IllegalArgumentException("Expected object")
}

fun JsonObject.string(name: String): String =
    (fields[name] as? JsonString)?.value ?: throw IllegalArgumentException("Expected string: $name")

fun JsonObject.objectValue(name: String): JsonObject =
    fields[name] as? JsonObject ?: throw IllegalArgumentException("Expected object: $name")

fun JsonObject.array(name: String): List<JsonValue> =
    (fields[name] as? JsonArray)?.items ?: throw IllegalArgumentException("Expected array: $name")
