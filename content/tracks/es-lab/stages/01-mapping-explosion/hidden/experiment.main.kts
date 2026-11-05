@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("../starter/diagnostics/json.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess
val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
fun request(base: String, method: String, path: String, body: String = ""): HttpResponse<String> = client.send(
    HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20))
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
    HttpResponse.BodyHandlers.ofString(),
)
fun es(base: String, method: String, path: String, body: String = ""): Map<*, *> {
    val response = request(base, method, path, body)
    check(response.statusCode() in 200..299) { "Elasticsearch $method $path: ${response.statusCode()} ${response.body().take(300)}" }
    return decode(response.body())
}

fun countFields(mapping: Map<*, *>): Int = listOf("properties", "fields").sumOf { group ->
    (mapping[group] as? Map<*, *>)?.values?.sumOf { field -> 1 + countFields(obj(field)) } ?: 0
} + ((mapping["runtime"] as? Map<*, *>)?.size ?: 0)

fun fieldCount(base: String, index: String): Int =
    countFields(obj(obj(es(base, "GET", "/$index/_mapping")[index])["mappings"]))

data class Observation(val firstRejected: Int, val fields: Int, val documents: Int)
fun observe(base: String): Observation {
    val index = "mapping-observe"
    es(base, "PUT", "/$index", """{"settings":{"number_of_shards":1,"number_of_replicas":0,
      "index.mapping.total_fields.limit":12,"index.mapping.total_fields.ignore_dynamic_beyond_limit":false},
      "mappings":{"dynamic":true,"date_detection":false,"numeric_detection":false,"properties":{
        "id":{"type":"keyword"},"seller":{"type":"keyword"},"attributes":{"type":"object"}}}}""")
    var firstRejected = 0
    for (number in 1..6) {
        val key = if (number == 6) "k1" else "k$number"
        val body = """{"id":"o$number","seller":"s1","attributes":{"$key":"plain-value"}}"""
        val response = request(base, "PUT", "/$index/_doc/o$number", body)
        if (response.statusCode() == 400 && response.body().contains("Limit of total fields")) {
            if (firstRejected == 0) firstRejected = number
        } else check(response.statusCode() == 201) { "Unexpected observation response: ${response.body()}" }
    }
    es(base, "POST", "/$index/_refresh")
    val count = (es(base, "GET", "/$index/_count")["count"] as Number).toInt()
    for (number in listOf(1, 2, 3, 4, 6)) {
        val source = obj(es(base, "GET", "/$index/_doc/o$number")["_source"])
        check(source["id"] == "o$number" && source["seller"] == "s1")
        check(obj(source["attributes"]) == mapOf((if (number == 6) "k1" else "k$number") to "plain-value"))
    }
    check(request(base, "GET", "/$index/_doc/o5").statusCode() == 404)
    val result = Observation(firstRejected, fieldCount(base, index), count)
    check(result == Observation(5, 11, 5)) { "Observation fixture disagrees with documented mapping rules: $result" }
    return result
}

data class Product(val id: String, val seller: String, val attributes: Map<String, String>) {
    fun body(): String = """{"seller":${quote(seller)},"attributes":${encode(attributes)}}"""
}
data class Query(val seller: String?, val filters: Map<String, String>) {
    fun body(): String {
        val pairs = filters.entries.joinToString(",", "[", "]") { (key, value) ->
            """{"key":${quote(key)},"value":${quote(value)}}"""
        }
        return "{" + (seller?.let { "\"seller\":${quote(it)}," } ?: "") + "\"filters\":$pairs}"
    }
    fun matches(product: Product): Boolean = (seller == null || seller == product.seller) &&
        filters.all { (key, value) -> product.attributes[key] == value }
}

fun products(): List<Product> = List(120) { number ->
    Product("p${number.toString().padStart(3, '0')}", "s${number % 4}", linkedMapOf(
        "seller_${number}_a" to "own-$number",
        "seller_${number}_b" to "shared-${number % 7}",
        "seller_${number}_c" to if (number % 2 == 0) "한글 \"상품\"\\재질" else "x".repeat(300),
        "color" to if (number % 2 == 0) "red" else "blue",
        "size" to if (number % 2 == 0) "blue" else "red",
    ))
}

fun queries(): List<Query> = buildList {
    add(Query(null, emptyMap()))
    for (seller in 0..3) add(Query("s$seller", emptyMap()))
    for (product in products()) {
        for ((key, value) in product.attributes) {
            if (key != "color" && key != "size") add(Query(null, mapOf(key to value)))
        }
    }
    for (seller in listOf(null, "s0", "s1", "s2", "s3", "absent")) {
        add(Query(seller, mapOf("color" to "red")))
        add(Query(seller, mapOf("color" to "red", "size" to "blue")))
        add(Query(seller, mapOf("color" to "red", "size" to "red")))
    }
    add(Query(null, mapOf("missing" to "red")))
    add(Query(null, mapOf("color" to "Red")))
    add(Query(null, mapOf("seller_0_c" to "한글 \"상품\"\\재질")))
    add(Query(null, mapOf("seller_1_c" to "x".repeat(300))))
    add(Query(null, mapOf("color" to "red", "size" to "blue", "seller_0_a" to "own-0")))
    add(Query("s3", mapOf("replacement" to "new-value")))
}

fun searchMismatches(base: String, products: List<Product>): Int {
    val queries = queries()
    val results = runLoad(client, queries.mapIndexed { number, query ->
        Arrival(number * 50L, post(base, "/search", query.body(), Duration.ofSeconds(20)))
    })
    return results.zip(queries).count { (result, query) ->
        val expected = products.filter(query::matches).map { it.id }.sorted()
        result.status != 200 || runCatching { items(decode(result.body)["ids"]) != expected }.getOrDefault(true)
    }
}

fun persistenceMismatches(base: String, index: String, products: List<Product>): Int {
    val response = es(base, "POST", "/$index/_search", """{"size":1000,"query":{"match_all":{}}}""")
    val hits = items(obj(response["hits"])["hits"])
    val sources = hits.associate { hit -> obj(hit)["_id"] to obj(obj(hit)["_source"]) }
    val missing = products.count { product ->
        val source = sources[product.id]
        val attributes = source?.get("attributes")
        val normalized = if (attributes is Map<*, *>) attributes else (attributes as? List<*>)?.associate { pair ->
            obj(pair)["key"] to obj(pair)["value"]
        }
        source?.get("id") != product.id || source?.get("seller") != product.seller || normalized != product.attributes
    }
    return missing + sources.keys.count { id -> products.none { it.id == id } }
}

fun policyErrors(base: String, index: String): Int {
    val settings = obj(obj(es(base, "GET", "/$index/_settings?flat_settings=true")[index])["settings"])
    val mapping = obj(obj(es(base, "GET", "/$index/_mapping")[index])["mappings"])
    return listOf(
        settings["index.mapping.total_fields.limit"] != "12",
        settings["index.mapping.total_fields.ignore_dynamic_beyond_limit"]?.toString() !in listOf(null, "false"),
        mapping["runtime"] != null,
    ).count { it }
}

fun experiment(program: File): Boolean = ComposeStack(File("compose.yaml"), "es-mapping").use { stack ->
    stack.up(240)
    val elasticBase = "http://127.0.0.1:${stack.port("elasticsearch", 9200)}"
    val observed = observe(elasticBase)
    val base = "http://127.0.0.1:${freePort()}"
    val index = "catalog"
    val environment = mapOf("PORT" to URI.create(base).port.toString(), "ES_URL" to elasticBase, "ES_INDEX" to index)
    val original = products()
    val products = original.toMutableList()
    var initialFields = 0
    var accepted = 0
    var mismatches = 0
    Program(program, environment, base).use {
        initialFields = fieldCount(elasticBase, index)
        val arrivals = steady(10, 12.0) { number ->
            HttpRequest.newBuilder(URI.create("$base/products/${products[number].id}"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(products[number].body())).build()
        }
        accepted = runLoad(client, arrivals).count { it.status == 201 }
        if (request(base, "POST", "/refresh").statusCode() != 200) mismatches++
        mismatches += searchMismatches(base, products)
        products[0] = Product("p000", "s3", emptyMap())
        products[1] = Product("p001", "s3", mapOf("replacement" to "new-value"))
        for (replacement in products.take(2)) {
            if (request(base, "PUT", "/products/${replacement.id}", replacement.body()).statusCode() != 201) mismatches++
        }
        if (request(base, "POST", "/refresh").statusCode() != 200) mismatches++
    }
    Program(program, environment, base).use { mismatches += searchMismatches(base, products) }
    val stored = (es(elasticBase, "GET", "/$index/_count")["count"] as Number).toInt()
    val finalFields = fieldCount(elasticBase, index)
    val persistence = persistenceMismatches(elasticBase, index, products)
    val policy = policyErrors(elasticBase, index)
    metric("first_rejected", observed.firstRejected)
    metric("observed_fields", observed.fields)
    metric("observed_documents", observed.documents)
    metric("accepted_products", accepted)
    metric("stored_products", stored)
    metric("initial_fields", initialFields)
    metric("final_fields", finalFields)
    metric("search_mismatches", mismatches)
    metric("persistence_mismatches", persistence)
    metric("policy_errors", policy)
    accepted == 120 && stored == 120 && initialFields in 1..8 && finalFields == initialFields &&
        mismatches == 0 && persistence == 0 && policy == 0
}

try {
    require(args.size == 2 && args[1] == "seller-catalog")
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    exitProcess(if (experiment(program)) 0 else 1)
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.message}")
    exitProcess(2)
}
