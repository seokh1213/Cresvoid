import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

data class Event(val id: Long, val value: Long)

class InsertRejected(val httpStatus: Int, val databaseCode: Int) :
    IllegalStateException("ClickHouse insert rejected: http=$httpStatus code=$databaseCode")

class ClickHouse(private val origin: String, private val table: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    init {
        require(table.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_][a-zA-Z0-9_]*")))
    }

    fun insert(events: List<Event>) {
        if (events.isEmpty()) return
        val query = URLEncoder.encode("INSERT INTO $table (id, value) FORMAT TabSeparated", StandardCharsets.UTF_8)
        val body = events.joinToString("\n", postfix = "\n") { "${it.id}\t${it.value}" }
        val request = HttpRequest.newBuilder(URI.create("$origin/?async_insert=0&wait_end_of_query=1&query=$query"))
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            val code = response.headers().firstValue("X-ClickHouse-Exception-Code").orElse("")
                .toIntOrNull() ?: 0
            throw InsertRejected(response.statusCode(), code)
        }
    }
}
