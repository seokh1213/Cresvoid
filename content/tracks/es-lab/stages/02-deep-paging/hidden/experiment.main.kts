@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.BitSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
val count = 500_000
val index = "products"
fun number(body: String, field: String): Int? =
    Regex("\"$field\"\\s*:\\s*(-?[0-9]+)").find(body)?.groupValues?.get(1)?.toInt()
fun padding(id: Int): String = "product-$id-" + "x".repeat(256)
fun source(id: Int, rank: Int = id / 10, revision: Int = 0): String =
    """{"id":$id,"rank":$rank,"revision":$revision,"payload":"${padding(id)}"}"""
fun request(base: String, method: String, path: String, body: String = ""): HttpResponse<String> =
    client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(120))
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
fun requireSuccess(response: HttpResponse<String>) {
    check(response.statusCode() in 200..299) { "Elasticsearch HTTP ${response.statusCode()}: ${response.body().take(500)}" }
    check(!Regex("\"errors\"\\s*:\\s*true").containsMatchIn(response.body())) { "Bulk item failure" }
}
fun seed(base: String) {
    requireSuccess(request(base, "PUT", "/$index", """
        {"settings":{"number_of_shards":3,"number_of_replicas":0,"refresh_interval":"-1"},
         "mappings":{"dynamic":"strict","properties":{"id":{"type":"long"},"rank":{"type":"long"},
         "revision":{"type":"integer"},"payload":{"type":"keyword","index":false,"doc_values":false}}}}
    """.trimIndent()))
    for (start in 0 until count step 5000) {
        val bulk = buildString {
            for (id in start until start + 5000) {
                append("""{"index":{"_id":"$id"}}""").append('\n')
                append(source(id)).append('\n')
            }
        }
        requireSuccess(request(base, "POST", "/$index/_bulk", bulk))
    }
    requireSuccess(request(base, "POST", "/$index/_refresh"))
    check(number(request(base, "GET", "/$index/_count").body(), "count") == count)
}
fun changeBody(round: Int, batch: Int): String = buildString {
    for (offset in batch * 250 until (batch + 1) * 250) {
        val update = round * 100000 + 1000 + offset
        val delete = round * 100000 + 10000 + offset
        val added = count + round * 1000 + offset
        append("""{"index":{"_id":"$update"}}""").append('\n')
        append(source(update, -1, 1)).append('\n')
        append("""{"delete":{"_id":"$delete"}}""").append('\n')
        append("""{"index":{"_id":"$added"}}""").append('\n')
        append(source(added, -1, 1)).append('\n')
    }
}

class Gateway(private val upstream: String) : AutoCloseable {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val searches = AtomicInteger()
    val maxWindow = AtomicInteger()
    val maxHits = AtomicInteger()
    val writes = AtomicInteger()
    val violations = AtomicInteger()
    val failure = AtomicReference<Throwable?>()
    val base: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            try {
                val path = exchange.requestURI.toString()
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                val allowed = path == "/_search" || path == "/$index/_search" ||
                    path.startsWith("/$index/_pit?") || path == "/_pit"
                if (!allowed) {
                    violations.incrementAndGet()
                    exchange.sendResponseHeaders(403, -1)
                } else {
                    val response = request(upstream, exchange.requestMethod, path, body)
                    if (path.endsWith("/_search")) observe(body, response)
                    val bytes = response.body().toByteArray()
                    exchange.sendResponseHeaders(response.statusCode(), bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                }
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
                runCatching { exchange.sendResponseHeaders(502, -1) }
            } finally {
                exchange.close()
            }
        }
        server.start()
    }

    private fun observe(body: String, response: HttpResponse<String>) {
        val page = searches.incrementAndGet()
        val size = number(body, "size") ?: 10
        maxWindow.accumulateAndGet((number(body, "from") ?: 0) + size, ::maxOf)
        maxHits.accumulateAndGet(Regex("\"_id\"\\s*:").findAll(response.body()).count(), ::maxOf)
        if (page in listOf(1, 100, 200) && response.statusCode() == 200) {
            val round = listOf(1, 100, 200).indexOf(page)
            val outcomes = runLoad(client, steady(4, 1.0) { batch ->
                post(upstream, "/$index/_bulk?refresh=true", changeBody(round, batch),
                    Duration.ofSeconds(90), mapOf("Content-Type" to "application/x-ndjson"))
            })
            check(outcomes.all { it.status == 200 && !Regex("\"errors\"\\s*:\\s*true").containsMatchIn(it.body) })
            writes.addAndGet(outcomes.size)
        }
    }

    override fun close() {
        server.stop(0)
        executor.close()
    }
}

data class ExportResult(val missing: Int, val duplicate: Int, val invalid: Int, val status: Int, val millis: Double)
fun collect(base: String, limit: Duration = Duration.ofSeconds(240)): ExportResult {
    val seen = BitSet(count)
    var duplicate = 0
    var invalid = 0
    var status = 0
    val start = System.nanoTime()
    val body = AtomicReference<InputStream?>()
    val expired = AtomicBoolean()
    val timer = Executors.newSingleThreadScheduledExecutor()
    // HttpRequest.timeout stops waiting for headers but does not bound an InputStream body.
    val deadline = timer.schedule({
        expired.set(true)
        body.get()?.close()
    }, limit.toNanos(), TimeUnit.NANOSECONDS)
    try {
        val response = client.send(get(base, "/export", limit), HttpResponse.BodyHandlers.ofInputStream())
        status = response.statusCode()
        body.set(response.body())
        if (expired.get()) response.body().close()
        response.body().bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split('\t', limit = 3)
                val id = parts.firstOrNull()?.toIntOrNull()
                if (parts.size != 3 || id == null || id !in 0 until count) {
                    invalid++
                } else {
                    if (seen[id]) duplicate++
                    seen.set(id)
                    if (parts[1] != "0" || parts[2] != padding(id)) invalid++
                }
            }
        }
    } catch (_: IOException) {
        invalid++
    } finally {
        deadline.cancel(false)
        timer.shutdownNow()
    }
    val millis = (System.nanoTime() - start) / 1e6
    return ExportResult(count - seen.cardinality(), duplicate, invalid, status, millis)
}
fun experiment(program: File): Boolean {
    ComposeStack(File("compose.yaml"), "es-deep-paging").use { stack ->
        stack.up(240)
        val upstream = "http://127.0.0.1:${stack.port("elasticsearch", 9200)}"
        seed(upstream)
        Gateway(upstream).use { gateway ->
            val port = freePort()
            val base = "http://127.0.0.1:$port"
            val environment = mapOf("PORT" to "$port", "ES_URL" to gateway.base, "ES_INDEX" to index,
                "JAVA_TOOL_OPTIONS" to "-Xmx192m")
            Program(program, environment, base).use {
                val result = collect(base)
                gateway.failure.get()?.let { throw it }
                // A search response can precede the export handler's finally block.
                val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
                var contexts = 0
                do {
                    val stats = request(upstream, "GET", "/_nodes/stats/indices/search")
                    requireSuccess(stats)
                    val counts = Regex("\"open_contexts\"\\s*:\\s*(\\d+)")
                        .findAll(stats.body()).map { it.groupValues[1].toInt() }.toList()
                    check(counts.isNotEmpty()) { "Node statistics omitted open_contexts" }
                    contexts = counts.sum()
                    if (contexts == 0) break
                    Thread.sleep(50)
                } while (System.nanoTime() < deadline)
                val errors = result.invalid + if (result.status == 200) 0 else 1
                metric("missing", result.missing)
                metric("duplicates", result.duplicate)
                metric("invalid_rows", errors)
                metric("search_requests", gateway.searches.get())
                metric("max_window", gateway.maxWindow.get())
                metric("max_page_hits", gateway.maxHits.get())
                metric("write_batches", gateway.writes.get())
                metric("open_contexts", contexts)
                metric("forbidden_requests", gateway.violations.get())
                metric("export_ms", result.millis)
                return result.missing == 0 && result.duplicate == 0 && errors == 0 &&
                    gateway.searches.get() in 500..520 && gateway.maxWindow.get() <= 10000 &&
                    gateway.maxHits.get() <= 1000 && gateway.writes.get() == 12 && contexts == 0 &&
                    gateway.violations.get() == 0 && result.millis <= 240000
            }
        }
    }
}

try {
    require(args.size == 2 && File(args[0]).isAbsolute && args[1] == "catalog-export")
    requireDocker()
    exitProcess(if (experiment(File(args[0]))) 0 else 1)
} catch (error: Throwable) {
    System.err.println("Experiment error: ${error.javaClass.simpleName}: ${error.message}")
    exitProcess(2)
}
