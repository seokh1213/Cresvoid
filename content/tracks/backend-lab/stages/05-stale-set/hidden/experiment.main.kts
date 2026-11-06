@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class ProductValue(val id: Int, val price: Long, val version: Long)
data class Counts(var responses: Int = 0, var stale: Int = 0, var errors: Int = 0)

fun product(value: String): ProductValue? {
    val fields = value.trim().split(',')
    if (fields.size != 3) return null
    val id = fields[0].toIntOrNull() ?: return null
    val price = fields[1].toLongOrNull() ?: return null
    val version = fields[2].toLongOrNull() ?: return null
    return ProductValue(id, price, version)
}

fun control(client: HttpClient, request: HttpRequest): String {
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "control failed: ${request.uri().path}" }
    return response.body()
}

fun priceRequest(base: String, id: Int, price: Long): HttpRequest =
    HttpRequest.newBuilder(URI.create("$base/products/$id/price"))
        .timeout(Duration.ofSeconds(8))
        .PUT(HttpRequest.BodyPublishers.ofString(price.toString())).build()

fun countResponses(outcomes: List<Outcome>, expected: Map<Int, ProductValue>, counts: Counts) {
    for (outcome in outcomes) {
        counts.responses++
        val id = outcome.arrival.request.uri().path.substringAfter("/products/").substringBefore('/').toInt()
        val received = product(outcome.body)
        val baseline = expected.getValue(id)
        if (outcome.status != 200 || outcome.error != null || received == null || received.id != id ||
            received.version !in 0..baseline.version || received.price != 100L + received.version) {
            counts.errors++
        } else if (received.version < baseline.version && outcome.arrival.request.method() == "GET") {
            counts.stale++
        } else if (received != baseline) {
            counts.errors++
        }
    }
}

fun countDelayedResponses(
    outcomes: List<Outcome>,
    before: Map<Int, ProductValue>,
    after: Map<Int, ProductValue>,
    counts: Counts,
) {
    // These reads started before the confirmed writes, so their old snapshots are permitted responses.
    for (outcome in outcomes) {
        counts.responses++
        val id = outcome.arrival.request.uri().path.substringAfter("/products/").toInt()
        val received = product(outcome.body)
        if (outcome.status != 200 || outcome.error != null || received == null ||
            received !in listOf(before[id], after[id])) counts.errors++
    }
}

fun awaitSnapshots(client: HttpClient, base: String) {
    val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
    while (System.nanoTime() < deadline) {
        if (control(client, get(base, "/admin/pending")).toInt() == 12) return
        Thread.sleep(20)
    }
    error("12 source snapshots were not reached")
}

fun exerciseRound(client: HttpClient, base: String, round: Int, counts: Counts) {
    val before = (1..12).associateWith { ProductValue(it, if (round == 1) 100 else 100L + round - 1, round - 1L) }
    val after = (1..12).associateWith { ProductValue(it, 100L + round, round.toLong()) }
    control(client, post(base, "/admin/hold", ""))
    Executors.newSingleThreadExecutor().use { executor ->
        val readers = executor.submit<List<Outcome>> {
            runLoad(client, (1..12).map { Arrival(0, get(base, "/products/$it", Duration.ofSeconds(25))) })
        }
        try {
            awaitSnapshots(client, base)
            val writes = runLoad(client, (1..12).map { Arrival(0, priceRequest(base, it, 100L + round)) })
            countResponses(writes, after, counts)
        } finally {
            control(client, post(base, "/admin/release", ""))
        }
        val delayed = readers.get(10, TimeUnit.SECONDS)
        countDelayedResponses(delayed, before, after, counts)
    }
    val probes = runLoad(client, steady(80, 3.0) { index -> get(base, "/products/${index % 12 + 1}") })
    countResponses(probes, after, counts)
}

fun mismatches(body: String, round: Int): Int {
    val rows = body.trim().lines()
    if (rows.size != 12) return 12
    val seen = mutableSetOf<Int>()
    var errors = 0
    for (row in rows) {
        val fields = row.split('|')
        val origin = product(fields[0])
        val expected = origin?.let { ProductValue(it.id, 100L + round, round.toLong()) }
        if (fields.size != 2 || origin == null || origin.id !in 1..12 || !seen.add(origin.id) || origin != expected) {
            errors++
        } else if (fields[1] != "empty" && product(fields[1]) != origin) {
            errors++
        }
    }
    return errors
}

fun cacheUseErrors(client: HttpClient, base: String, rounds: Int): Int {
    val expected = (1..12).associateWith { ProductValue(it, 100L + rounds, rounds.toLong()) }
    val counts = Counts()
    val requests = (1..12).map { Arrival(0, get(base, "/products/$it")) }
    countResponses(runLoad(client, requests), expected, counts)
    val state = control(client, get(base, "/admin/state"))
    val missing = state.lines().count { it.endsWith("|empty") }
    val before = control(client, get(base, "/admin/reads")).toInt()
    countResponses(runLoad(client, requests), expected, counts)
    val after = control(client, get(base, "/admin/reads")).toInt()
    return counts.errors + counts.stale + mismatches(state, rounds) + missing + (after - before)
}

fun experiment(program: File, rounds: Int): Int {
    ComposeStack(File("compose.yaml"), "backend-stale-set").use { stack ->
        stack.up()
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        val environment = mapOf("PORT" to "$port", "REDIS_HOST" to "127.0.0.1", "REDIS_PORT" to "${stack.port("redis", 6379)}")
        Program(program, environment, base).use {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
                val counts = Counts()
                for (round in 1..rounds) exerciseRound(client, base, round, counts)
                val began = System.nanoTime()
                var firstClean = -1L
                var remaining: Int
                do {
                    remaining = mismatches(control(client, get(base, "/admin/state")), rounds)
                    val elapsed = (System.nanoTime() - began) / 1_000_000
                    if (remaining == 0 && firstClean < 0) firstClean = elapsed
                    if (remaining != 0) firstClean = -1
                    if (elapsed >= 2_000) break
                    Thread.sleep(minOf(100, 2_000 - elapsed))
                } while (true)
                val cacheErrors = cacheUseErrors(client, base, rounds)
                metric("responses", counts.responses)
                metric("response_errors", counts.errors)
                metric("stale_responses", counts.stale)
                metric("stale_ratio", counts.stale.toDouble() / (rounds * 240))
                metric("cache_mismatches", remaining)
                metric("convergence_ms", if (firstClean < 0) 2_001 else firstClean)
                metric("cache_use_errors", cacheErrors)
                return if (counts.responses == rounds * 264 && counts.errors == 0 &&
                    counts.stale * 100 <= rounds * 240 && remaining == 0 &&
                    firstClean in 0..2_000 && cacheErrors == 0) 0 else 1
            }
        }
    }
}

val code = try {
    require(args.size == 2 && File(args[0]).isAbsolute)
    val rounds = when (args[1]) {
        "shop-browse" -> 1
        "price-rounds" -> 3
        else -> error("unknown scenario")
    }
    requireDocker()
    experiment(File(args[0]), rounds)
} catch (error: Exception) {
    System.err.println("experiment error: ${error.message}")
    2
}
exitProcess(code)
