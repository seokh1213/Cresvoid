@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

class Json(private val text: String) {
    private var at = 0
    fun parse(): Any? = value().also { whitespace(); require(at == text.length) }
    private fun whitespace() { while (at < text.length && text[at].isWhitespace()) at++ }
    private fun take(): Char = text[at++]
    private fun value(): Any? {
        whitespace()
        return when (text[at]) {
            '{' -> objectValue()
            '[' -> arrayValue()
            '"' -> stringValue()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> numberValue()
        }
    }
    private fun literal(word: String, result: Any?): Any? {
        require(text.startsWith(word, at)); at += word.length; return result
    }
    private fun objectValue(): Map<String, Any?> {
        take(); whitespace()
        val result = linkedMapOf<String, Any?>()
        if (text[at] == '}') { take(); return result }
        while (true) {
            whitespace(); val key = stringValue(); whitespace(); require(take() == ':')
            result[key] = value(); whitespace()
            when (take()) { '}' -> return result; ',' -> Unit; else -> error("object separator") }
        }
    }
    private fun arrayValue(): List<Any?> {
        take(); whitespace()
        val result = mutableListOf<Any?>()
        if (text[at] == ']') { take(); return result }
        while (true) {
            result.add(value()); whitespace()
            when (take()) { ']' -> return result; ',' -> Unit; else -> error("array separator") }
        }
    }
    private fun stringValue(): String {
        require(take() == '"')
        return buildString {
            while (true) {
                val char = take()
                if (char == '"') break
                if (char != '\\') { append(char); continue }
                append(when (val escaped = take()) {
                    '"', '\\', '/' -> escaped
                    'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                    'u' -> text.substring(at, at + 4).toInt(16).toChar().also { at += 4 }
                    else -> error("string escape")
                })
            }
        }
    }
    private fun numberValue(): Number {
        val begin = at
        while (at < text.length && text[at] in "-+0123456789.eE") at++
        require(at > begin)
        val number = text.substring(begin, at)
        return number.toLongOrNull() ?: number.toDouble().also { require(it.isFinite()) }
    }
}

fun Any?.obj(): Map<*, *> = this as? Map<*, *> ?: error("expected object")
fun Any?.array(): List<*> = this as? List<*> ?: error("expected array")
fun Any?.integer(): Long = this as? Long ?: error("expected integer")
data class Bucket(val key: String, val count: Long, val error: Long)
data class Report(val error: Long, val other: Long, val buckets: List<Bucket>)
data class Setting(val size: Int, val shardSize: Int?) {
    fun path(): String = "/brands?size=$size" + (shardSize?.let { "&shard_size=$it" } ?: "")
}
val settings = listOf(Setting(10, null), Setting(10, 10), Setting(10, 64),
    Setting(20, null), Setting(20, 20), Setting(20, 64))

fun report(body: String): Report {
    val root = Json(body).parse().obj()
    require(root["timed_out"] == false)
    val shards = root["_shards"].obj()
    require(shards["total"].integer() == 5L && shards["successful"].integer() == 5L)
    require(shards["failed"].integer() == 0L)
    require(root["hits"].obj()["hits"].array().isEmpty())
    val aggregation = root["aggregations"].obj()["brands"].obj()
    val buckets = aggregation["buckets"].array().map { item ->
        val bucket = item.obj()
        Bucket(bucket["key"] as String, bucket["doc_count"].integer(),
            bucket["doc_count_error_upper_bound"].integer())
    }
    return Report(aggregation["doc_count_error_upper_bound"].integer(),
        aggregation["sum_other_doc_count"].integer(), buckets)
}

fun request(client: HttpClient, url: String, method: String, body: String = ""): String {
    val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90))
        .header("Content-Type", if (url.contains("_bulk")) "application/x-ndjson" else "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body)).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() in 200..299) { "Elasticsearch $method failed: ${response.statusCode()}" }
    return response.body()
}

fun routes(client: HttpClient, base: String): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    for (candidate in 0 until 200) {
        val routing = "route-$candidate"
        val root = Json(request(client, "$base/_search_shards?routing=$routing", "GET")).parse().obj()
        val shard = root["shards"].array().single().array().first().obj()["shard"].integer().toInt()
        result.putIfAbsent(shard, routing)
        if (result.size == 5) return result
    }
    error("could not route to all five shards")
}

fun seed(client: HttpClient, base: String): Map<String, Long> {
    request(client, base, "PUT", """{"settings":{"number_of_shards":5,"number_of_routing_shards":5,"number_of_replicas":0,"refresh_interval":"-1"},"mappings":{"properties":{"brand":{"type":"keyword"}}}}""")
    val routing = routes(client, base)
    val truth = linkedMapOf<String, Long>()
    var id = 0
    val bulk = buildString {
        for (shard in 0..4) {
            val counts = (1..30).associate { j -> "local-$shard-${j.toString().padStart(2, '0')}" to 110 - j } +
                mapOf("atlas" to 79, "beacon" to if (shard == 0) 120 else 30)
            for ((brand, count) in counts) {
                truth[brand] = truth.getOrDefault(brand, 0L) + count
                repeat(count) {
                    append("""{"index":{"_id":"${id++}","routing":"${routing.getValue(shard)}"}}""").append('\n')
                    append("""{"brand":"$brand"}""").append('\n')
                }
            }
        }
    }
    val result = Json(request(client, "$base/_bulk", "POST", bulk)).parse().obj()
    check(result["errors"] == false)
    request(client, "$base/_refresh", "POST")
    val count = Json(request(client, "$base/_count", "GET")).parse().obj()["count"].integer()
    check(count == 14810L && count == truth.values.sum() && truth.size == 152)
    return truth
}

fun reference(client: HttpClient, base: String, setting: Setting): Report {
    val candidates = setting.shardSize?.let { ",\"shard_size\":$it" }.orEmpty()
    val body = """{"size":0,"aggs":{"brands":{"terms":{"field":"brand","order":{"_count":"desc"},"show_term_doc_count_error":true,"size":${setting.size}$candidates}}}}"""
    return report(request(client, "$base/_search", "POST", body))
}

fun boundViolations(actual: Report, truth: Map<String, Long>): Int = actual.buckets.count { bucket ->
    val exact = truth[bucket.key]
    exact == null || bucket.count < 0 || bucket.error < 0 || bucket.error > actual.error ||
        bucket.count > exact || exact - bucket.count > bucket.error
} + if (actual.error < 0 || actual.other < 0) 1 else 0

fun verifyFixture(expected: List<Report>, truth: Map<String, Long>) {
    check(expected[0].buckets.none { it.key == "atlas" })
    check(expected[0].error > 0 && expected[0].buckets.first { it.key == "beacon" }.count < truth.getValue("beacon"))
    val ranked = truth.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    for (index in listOf(2, 3, 5)) {
        val sample = expected[index]
        check(sample.error == 0L && sample.other > 0)
        check(sample.buckets == ranked.take(settings[index].size).map { Bucket(it.key, it.value, 0) })
        check(sample.other + sample.buckets.sumOf { it.count } == truth.values.sum())
    }
    check(expected.all { boundViolations(it, truth) == 0 })
}

fun measure(client: HttpClient, base: String, expected: List<Report>, truth: Map<String, Long>): Int {
    val arrivals = steady(4, 3.0) { index -> get(base, settings[index % settings.size].path(), Duration.ofSeconds(20)) }
    val outcomes = runLoad(client, arrivals)
    val reports = outcomes.map { outcome ->
        if (outcome.status == 200) runCatching { report(outcome.body) }.getOrNull() else null
    }
    val responsesOk = reports.count { it != null }
    val mismatches = reports.indices.count { reports[it] != expected[it % settings.size] }
    val firstMismatch = reports.indices.firstOrNull { reports[it] != expected[it % settings.size] }
    val bounds = reports.sumOf { if (it == null) 1 else boundViolations(it, truth) }
    val default = reports[0]
    val wide = reports[2]
    metric("requests", outcomes.size)
    metric("responses_ok", responsesOk)
    metric("report_mismatches", mismatches)
    metric("first_mismatch_setting", firstMismatch?.let { it % settings.size + 1 } ?: 0)
    metric("bound_violations", bounds)
    metric("default_missing", if (default?.buckets?.any { it.key == "atlas" } == false) 1 else 0)
    metric("default_error", default?.error ?: -1)
    metric("wide_error", wide?.error ?: -1)
    metric("wide_top_count", wide?.buckets?.firstOrNull()?.count ?: -1)
    metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
    return if (outcomes.size == 12 && responsesOk == 12 && mismatches == 0 && bounds == 0) 0 else 1
}

fun experiment(program: File): Int {
    requireDocker()
    ComposeStack(File("compose.yaml"), "es-terms").use { stack ->
        stack.up(240)
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
            val elastic = "http://127.0.0.1:${stack.port("elasticsearch", 9200)}"
            val index = "brand-products"
            val truth = seed(client, "$elastic/$index")
            val expected = settings.map { reference(client, "$elastic/$index", it) }
            verifyFixture(expected, truth)
            val boundary = reference(client, "$elastic/$index", Setting(10, 32))
            check(boundary.error == 199L && boundary.other == expected[2].other)
            check(boundary.buckets.map { it.copy(error = 0) } == expected[2].buckets)
            check(boundary.buckets.all { bucket ->
                bucket.error == when {
                    bucket.key == "atlas" || bucket.key == "beacon" -> 0L
                    bucket.key.startsWith("local-0-") -> 120L
                    else -> 169L
                }
            })
            val port = freePort()
            val base = "http://127.0.0.1:$port"
            Program(program, mapOf("PORT" to "$port", "ES_URL" to elastic, "ES_INDEX" to index), base).use {
                return measure(client, base, expected, truth)
            }
        }
    }
}

val code = try {
    require(args.size == 2 && File(args[0]).isAbsolute && args[1] == "brand-report")
    experiment(File(args[0]))
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(code)
