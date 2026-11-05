import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

fun JsonValue.obj(): Map<String, JsonValue> = (this as JsonObject).fields
fun JsonValue.array(): List<JsonValue> = (this as JsonArray).items
fun JsonValue.text(): String = (this as JsonString).value
fun json(text: String): JsonValue {
    val parsed = parseJson(text, KeyRule.ANY)
    check(!parsed.hasTrailingContent)
    return parsed.value
}
fun quoted(text: String): String = JsonString(text).toIndentedJson()

class Elastic(private val base: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun request(method: String, path: String, body: String = ""): JsonValue {
        val response = client.send(
            HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(response.statusCode() in 200..299) { "Elasticsearch HTTP ${response.statusCode()}" }
        return json(response.body())
    }
}

fun pageHits(response: JsonValue): List<JsonValue> {
    check(response.obj()["timed_out"] == JsonBoolean(false))
    val failed = response.obj().getValue("_shards").obj().getValue("failed") as JsonNumber
    check(failed.literal == "0")
    return response.obj().getValue("hits").obj().getValue("hits").array()
}
