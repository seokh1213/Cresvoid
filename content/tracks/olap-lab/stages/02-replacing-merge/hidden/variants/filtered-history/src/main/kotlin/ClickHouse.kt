import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ClickHouse(private val url: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    fun query(sql: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .POST(HttpRequest.BodyPublishers.ofString(sql))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "ClickHouse query failed" }
        return response.body()
    }
}
