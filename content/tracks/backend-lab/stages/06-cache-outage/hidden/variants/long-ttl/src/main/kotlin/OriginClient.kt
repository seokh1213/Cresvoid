import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OriginClient(private val base: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300)).build()

    fun get(id: Int): String? {
        val request = HttpRequest.newBuilder(URI.create("$base/products/$id"))
            .timeout(Duration.ofSeconds(2)).GET().build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body().takeIf { response.statusCode() == 200 }
        } catch (_: java.io.IOException) {
            null
        }
    }
}
