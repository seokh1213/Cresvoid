import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DownstreamClient(private val base: String) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(350)).build()

    fun fetch(id: String): Reply {
        repeat(1) { attempt ->
            val request = HttpRequest.newBuilder(URI.create("$base/quote/$id"))
                .timeout(Duration.ofMillis(350))
                .header("X-Attempt", attempt.toString())
                .header("X-Backoff-Ms", "0")
                .GET().build()
            val response = try {
                http.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (_: IOException) {
                null
            }
            if (response != null && response.statusCode() in setOf(200, 404)) {
                return Reply(response.statusCode(), response.body())
            }
        }
        return Reply(503, "{\"error\":\"unavailable\"}")
    }
}
