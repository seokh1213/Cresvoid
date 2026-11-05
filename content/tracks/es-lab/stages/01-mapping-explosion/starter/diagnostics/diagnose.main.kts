@file:Import("../../../../../../lab-env/load.main.kts")
@file:Import("../../../../../../lab-env/docker.main.kts")
@file:Import("json.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration
import kotlin.system.exitProcess

data class ProbeProduct(val id: String, val attributes: Map<String, String>) {
    fun body(): String = """{"seller":"s0","attributes":${encode(attributes)}}"""
}

class Diagnostics(private val base: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val counts = linkedMapOf(
        "write_errors" to 0, "refresh_errors" to 0, "replacement_errors" to 0,
        "search_request_errors" to 0, "search_result_mismatches" to 0,
    )

    private fun send(method: String, path: String, body: String): Outcome {
        val request = HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body)).build()
        return runLoad(client, listOf(Arrival(0, request))).single()
    }

    private fun failure(kind: String, result: Outcome, body: String, expected: String) {
        val previous = counts.getValue(kind)
        counts[kind] = previous + 1
        if (previous != 0) return
        println("FIRST $kind")
        println("REQUEST ${result.arrival.request.method()} ${result.arrival.request.uri().path} $body")
        println("EXPECTED $expected")
        println("ACTUAL ${result.status} ${result.body} ${result.error.orEmpty()}")
    }

    fun put(product: ProbeProduct, kind: String = "write_errors") {
        val result = send("PUT", "/products/${product.id}", product.body())
        if (result.status != 201) failure(kind, result, product.body(), "201 {}")
    }

    fun refresh() {
        val result = send("POST", "/refresh", "")
        if (result.status != 200) failure("refresh_errors", result, "", "200 {}")
    }

    fun search(filters: Map<String, String>, products: List<ProbeProduct>) {
        val pairs = filters.entries.joinToString(",", "[", "]") { (key, value) ->
            """{"key":${quote(key)},"value":${quote(value)}}"""
        }
        val body = """{"filters":$pairs}"""
        val expected = products.filter { product -> filters.all { (key, value) -> product.attributes[key] == value } }
            .map { it.id }.sorted()
        val expectedBody = expected.joinToString(",", "{\"ids\":[", "]}", transform = ::quote)
        val result = send("POST", "/search", body)
        val actual = runCatching { items(decode(result.body)["ids"]).also { ids -> check(ids.all { it is String }) } }
        when {
            result.status != 200 || actual.isFailure ->
                failure("search_request_errors", result, body, "200 $expectedBody")
            actual.getOrThrow() != expected ->
                failure("search_result_mismatches", result, body, "200 $expectedBody")
        }
    }

    fun searchAll(products: List<ProbeProduct>) {
        search(emptyMap(), products)
        search(mapOf("color" to "red"), products)
        search(mapOf("color" to "red", "size" to "red"), products)
        for (number in 2..7) search(mapOf("unique_$number" to "value-$number"), products)
    }

    fun report(): Boolean {
        counts.forEach { (kind, count) -> println("COUNT $kind $count") }
        return counts.values.all { it == 0 }
    }
}

fun diagnose(program: File): Boolean {
    val compose = File("content/tracks/es-lab/stages/01-mapping-explosion/hidden/compose.yaml")
    require(compose.isFile) { "Run from the project root" }
    return ComposeStack(compose, "es-mapping-diagnose").use { stack ->
        stack.up(240)
        val base = "http://127.0.0.1:${freePort()}"
        val environment = mapOf(
            "PORT" to URI.create(base).port.toString(),
            "ES_URL" to "http://127.0.0.1:${stack.port("elasticsearch", 9200)}",
            "ES_INDEX" to "diagnose",
        )
        val products = mutableListOf(
            ProbeProduct("p000", mapOf("color" to "red", "size" to "blue")),
            ProbeProduct("p001", mapOf("color" to "blue", "size" to "red")),
        )
        for (number in 2..7) products += ProbeProduct("p00$number", mapOf("unique_$number" to "value-$number"))
        val diagnostics = Diagnostics(base)
        Program(program, environment, base).use {
            products.forEach { diagnostics.put(it) }
            diagnostics.refresh()
            diagnostics.searchAll(products)
            products[0] = ProbeProduct("p000", emptyMap())
            products[1] = ProbeProduct("p001", mapOf("replacement" to "new-value"))
            products.take(2).forEach { diagnostics.put(it, "replacement_errors") }
            diagnostics.refresh()
        }
        Program(program, environment, base).use { diagnostics.searchAll(products) }
        diagnostics.report()
    }
}

try {
    require(args.size == 1) { "Usage: diagnose.main.kts <program directory>" }
    val program = File(args[0]).absoluteFile
    require(File(program, "build/app.jar").isFile) { "Run ./build.sh in the program directory first" }
    exitProcess(if (diagnose(program)) 0 else 1)
} catch (error: Exception) {
    System.err.println("Diagnostic error: ${error.message}")
    exitProcess(2)
}
