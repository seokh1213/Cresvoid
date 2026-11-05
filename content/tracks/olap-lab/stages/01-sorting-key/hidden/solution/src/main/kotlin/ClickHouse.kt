import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

data class QueryResult(val id: String, val sum: Long)
data class ReadStats(val rows: Long, val marks: Long)

class ClickHouse(private val base: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun sql(query: String, id: String = UUID.randomUUID().toString()): String {
        val request = HttpRequest.newBuilder(URI.create("$base/?query_id=${URLEncoder.encode(id, Charsets.UTF_8)}"))
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(query)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "ClickHouse HTTP ${response.statusCode()}: ${response.body().take(300)}" }
        return response.body().trim()
    }

    fun stats(id: String): ReadStats {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        sql("SYSTEM FLUSH LOGS")
        val fields = sql("""
            SELECT read_rows, ProfileEvents['SelectedMarks'] FROM system.query_log
            WHERE query_id = '$id' AND type = 'QueryFinish' FORMAT TSV
        """.trimIndent()).split('\t')
        check(fields.size == 2)
        return ReadStats(fields[0].toLong(), fields[1].toLong())
    }
}
