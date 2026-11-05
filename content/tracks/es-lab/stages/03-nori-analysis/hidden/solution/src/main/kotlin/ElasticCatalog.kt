import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

fun jsonString(value: String): String = buildString {
    append('"')
    for (character in value) {
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
        }
    }
    append('"')
}

class ElasticCatalog(private val base: String) : AutoCloseable {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private val index = "nori-" + UUID.randomUUID().toString()
    private val profiles = setOf("none", "discard", "mixed", "plain", "catalog")

    init {
        val analysis = catalogAnalysis().trim().removePrefix("{").removeSuffix("}")
        val probeNames = listOf("none", "discard", "mixed", "plain")
        val probeTokenizers = probeNames.joinToString(",") { profile ->
            val mode = if (profile == "plain") "mixed" else profile
            val rules = if (profile == "plain") "" else
                ",\"user_dictionary_rules\":[\"초경량패딩 초경량 패딩\",\"노바핏\"]"
            """"probe_$profile":{"type":"nori_tokenizer","decompound_mode":"$mode"$rules}"""
        }
        val probeAnalyzers = probeNames.joinToString(",") { profile ->
            """"$profile":{"type":"custom","tokenizer":"probe_$profile"}"""
        }
        call("PUT", "/$index", """{
          "settings":{"number_of_shards":1,"number_of_replicas":0,"index.analyze.max_token_count":131072,"analysis":{$analysis}},
          "mappings":{"properties":{"title":{"type":"text","analyzer":"catalog","search_analyzer":"catalog"}}}
        }""")
        // Observation analyzers are independent of the learner's catalog settings.
        call("POST", "/$index/_close")
        call("PUT", "/$index/_settings", """{"analysis":{"tokenizer":{$probeTokenizers},"analyzer":{$probeAnalyzers}}}""")
        call("POST", "/$index/_open")
    }

    fun analyze(profile: String, text: String): String {
        require(profile in profiles)
        return call("POST", "/$index/_analyze", """{"analyzer":"$profile","text":${jsonString(text)}}""")
    }

    fun replaceProducts(body: String): String {
        val products = body.lineSequence().filter { it.isNotBlank() }.map { line ->
            val columns = line.split('\t', limit = 2)
            require(columns.size == 2 && columns[0].matches(Regex("[a-z0-9-]{1,40}")))
            require(columns[1].length in 1..200)
            columns[0] to columns[1]
        }.toList()
        require(products.size in 1..100 && products.map { it.first }.distinct().size == products.size)
        call("POST", "/$index/_delete_by_query?refresh=true", """{"query":{"match_all":{}}}""")
        val bulk = products.joinToString("\n", postfix = "\n") { (id, title) ->
            """{"index":{"_id":${jsonString(id)}}}""" + "\n" + """{"title":${jsonString(title)}}"""
        }
        val response = call("POST", "/$index/_bulk?refresh=true", bulk)
        check(Regex("\"errors\"\\s*:\\s*false").containsMatchIn(response)) { "bulk failed" }
        return """{"indexed":${products.size}}"""
    }

    fun search(query: String): String = call(
        "POST", "/$index/_search?filter_path=hits.hits._id", """{
          "size":100,"query":{"match":{"title":{"query":${jsonString(query)},"operator":"and","zero_terms_query":"none"}}}
        }""",
    )

    private fun call(method: String, path: String, body: String = ""): String {
        val request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", if (path.contains("_bulk")) "application/x-ndjson" else "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Elasticsearch ${response.statusCode()}: ${response.body().take(800)}" }
        return response.body()
    }

    override fun close() {
        try {
            call("DELETE", "/$index")
        } finally {
            client.close()
        }
    }
}
