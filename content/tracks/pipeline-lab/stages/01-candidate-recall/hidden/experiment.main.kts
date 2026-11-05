import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.system.exitProcess

data class Row(val id: Int, val category: String, val time: Long, val tokens: List<Int>)
data class Fixture(val rows: List<Row>, val p: Long, val q: Long) {
    fun body(): String = buildString {
        append("${rows.size} $p $q\n")
        rows.forEach { append("${it.id} ${it.category} ${it.time} ${it.tokens.joinToString(",").ifEmpty { "-" }}\n") }
    }
}
data class PairId(val a: Int, val b: Int)
data class Oracle(val pairs: Set<PairId>, val budget: Long, val minimumCalls: Long)
data class Observation(val response: HttpResponse<String>?, val millis: Double)
data class Prepared(val fixture: Fixture, val oracle: Oracle, val request: HttpRequest)
data class Pending(val fixture: Fixture, val oracle: Oracle, val future: CompletableFuture<Observation>)
data class Parsed(val pairs: Set<PairId>, val calls: Long)

fun primary(name: String): Fixture {
    val rows = mutableListOf<Row>()
    fun add(category: String, tokens: List<Int>) {
        val id = rows.size + 1
        rows.add(Row(id, category, (id % 7).toLong(), tokens))
    }
    when (name) {
        "collection-a" -> {
            repeat(6) { group -> repeat(10) { add("alpha", listOf(1, 2, 3, 10 + group)) } }
            add("alpha", (200..215).toList())
            repeat(12) { add("separate", listOf(100 + it)) }
            return Fixture(rows, 3, 5)
        }
        "collection-b" -> {
            repeat(36) { add("alpha", (1..8).toList() + (100 + it)) }
            repeat(12) { add("alpha", listOf(500 + it)) }
            return Fixture(rows, 4, 5)
        }
        "collection-c" -> {
            repeat(12) { add("c$it", (1..5).toList()) }
            repeat(12) { add("c$it", (1..6).toList()) }
            repeat(12) { add("c$it", (1..15).toList()) }
            return Fixture(rows, 4, 5)
        }
        else -> error("unknown scenario")
    }
}

fun probes(): List<Fixture> {
    fun row(id: Int, category: String, vararg tokens: Int) = Row(id, category, id.toLong(), tokens.toList())
    return listOf(
        Fixture(emptyList(), 1, 1),
        Fixture(listOf(row(1, "a")), 1, 1),
        Fixture(listOf(row(1, "a"), row(2, "a"), row(3, "a", 1), row(4, "b")), 1, 2),
        Fixture((1..4).map { row(it, "a", it * 10) }, 1, 2),
        Fixture(listOf(row(1, "a", 1, 2), row(2, "a", 1, 2, 3), row(3, "a", 2, 3)), 1, 2),
        Fixture(listOf(row(1, "a", 1, 2), row(2, "a", 1, 2, 3), row(3, "a", 8, 9),
            row(4, "b", 1, 2), row(5, "a"), row(6, "a", Int.MAX_VALUE)), 666666, 999999)
    )
}

// The oracle compares original records without using service grouping or candidate code.
fun truth(fixture: Fixture): Oracle {
    val sets = fixture.rows.map { it.tokens.toSet() }
    val pairs = mutableSetOf<PairId>()
    val matchingKeys = mutableSetOf<Set<String>>()
    fun key(row: Row): String = "${row.category}:${row.tokens.joinToString(",")}"
    for (i in fixture.rows.indices) {
        for (j in i + 1 until fixture.rows.size) {
            val a = fixture.rows[i]
            val b = fixture.rows[j]
            if (a.category != b.category) continue
            val intersection = sets[i].intersect(sets[j]).size.toLong()
            val union = sets[i].union(sets[j]).size.toLong()
            if (union != 0L && intersection * fixture.q < union * fixture.p) continue
            pairs.add(PairId(minOf(a.id, b.id), maxOf(a.id, b.id)))
            if (a.tokens != b.tokens) matchingKeys.add(setOf(key(a), key(b)))
        }
    }
    val keys = fixture.rows.distinctBy(::key)
    var budget = 0L
    for (i in keys.indices) {
        for (j in i + 1 until keys.size) {
            if (keys[i].category != keys[j].category) continue
            val sizes = listOf(keys[i].tokens.size.toLong(), keys[j].tokens.size.toLong())
            if (sizes.min() * fixture.q >= sizes.max() * fixture.p) budget++
        }
    }
    return Oracle(pairs, budget, matchingKeys.size.toLong())
}

fun parseResponse(body: String, fixture: Fixture): Parsed? {
    val lines = body.removeSuffix("\n").split('\n')
    if (!lines.first().matches(Regex("COMPARISONS [0-9]+"))) return null
    val calls = lines.first().substringAfter(' ').toLongOrNull() ?: return null
    if (calls !in 0L..124750L) return null
    val ids = fixture.rows.map { it.id }.toSet()
    val pairs = mutableSetOf<PairId>()
    var previous: PairId? = null
    for (line in lines.drop(1)) {
        val fields = line.split(' ')
        if (fields.size != 2) return null
        val a = fields[0].toIntOrNull() ?: return null
        val b = fields[1].toIntOrNull() ?: return null
        if (a !in ids || b !in ids || a >= b) return null
        val prior = previous
        if (prior != null && (a < prior.a || (a == prior.a && b <= prior.b))) return null
        val pair = PairId(a, b)
        if (!pairs.add(pair)) return null
        previous = pair
    }
    return Parsed(pairs, calls)
}

fun stopService(process: Process) {
    val descendants = process.descendants().toList()
    descendants.forEach { it.destroy() }
    process.destroy()
    if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
    process.waitFor(2, TimeUnit.SECONDS)
}

fun awaitReady(client: HttpClient, base: String, process: Process): Long {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
    val request = HttpRequest.newBuilder(URI.create("$base/health")).timeout(Duration.ofSeconds(1)).GET().build()
    while (System.nanoTime() < deadline) {
        check(process.isAlive) { "service exited before readiness" }
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200 && response.body() == "ready\n") return System.nanoTime()
        } catch (_: java.io.IOException) {
            // Connection refusal is expected until the listener binds.
        }
        Thread.sleep(40)
    }
    error("service readiness timeout")
}

fun prepareLoad(base: String, fixtures: List<Fixture>): List<Prepared> =
    fixtures.map { fixture ->
        val request = HttpRequest.newBuilder(URI.create("$base/pairs"))
            .timeout(Duration.ofSeconds(8)).header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(fixture.body())).build()
        Prepared(fixture, truth(fixture), request)
    }

fun sendLoad(client: HttpClient, inputs: List<Prepared>, readyAt: Long): List<Pending> {
    val start = readyAt + TimeUnit.MILLISECONDS.toNanos(100)
    return inputs.mapIndexed { index, input ->
        val scheduled = start + TimeUnit.MILLISECONDS.toNanos(index * 100L)
        while (System.nanoTime() < scheduled) LockSupport.parkNanos(scheduled - System.nanoTime())
        val sent = System.nanoTime()
        val future = client.sendAsync(input.request, HttpResponse.BodyHandlers.ofString()).handle { response, _ ->
            Observation(response, (System.nanoTime() - sent) / 1_000_000.0)
        }
        Pending(input.fixture, input.oracle, future)
    }
}

fun report(pending: List<Pending>): Int {
    var completed = 0
    var denominator = 0L
    var truePositives = 0L
    var missed = 0L
    var falsePositives = 0L
    var comparisons = 0L
    var budget = 0L
    var violations = 0
    var zeroRequests = 0
    val latencies = mutableListOf<Double>()
    for (item in pending) {
        val observation = item.future.get(10, TimeUnit.SECONDS)
        latencies.add(observation.millis)
        val expected = item.oracle
        denominator += expected.pairs.size
        budget += expected.budget
        if (expected.pairs.isEmpty()) zeroRequests++
        val response = observation.response
        val parsed = if (response?.statusCode() == 200) parseResponse(response.body(), item.fixture) else null
        if (parsed == null) {
            violations++
            missed += expected.pairs.size
            continue
        }
        completed++
        truePositives += parsed.pairs.count { it in expected.pairs }
        missed += expected.pairs.count { it !in parsed.pairs }
        falsePositives += parsed.pairs.count { it !in expected.pairs }
        comparisons += parsed.calls
        val rowsById = item.fixture.rows.associateBy { it.id }
        val emittedLinks = parsed.pairs.mapNotNull { pair ->
            val a = rowsById.getValue(pair.a)
            val b = rowsById.getValue(pair.b)
            val aKey = "${a.category}:${a.tokens}"
            val bKey = "${b.category}:${b.tokens}"
            if (aKey == bKey) null else setOf(aKey, bKey)
        }.toSet().size.toLong()
        if (parsed.calls !in emittedLinks..expected.budget) violations++
    }
    val p95 = latencies.sorted()[ceil(latencies.size * 0.95).toInt() - 1]
    if (completed != pending.size || p95 > 5000.0) violations++
    val recall = if (denominator == 0L) 0.0 else truePositives.toDouble() / denominator
    println("METRIC completed_requests $completed")
    println("METRIC recall $recall")
    println("METRIC recall_denominator $denominator")
    println("METRIC missed_pairs $missed")
    println("METRIC false_positives $falsePositives")
    println("METRIC comparisons $comparisons")
    println("METRIC comparison_budget $budget")
    println("METRIC p95_ms $p95")
    println("METRIC zero_reference_requests $zeroRequests")
    println("METRIC violations $violations")
    return if (missed == 0L && falsePositives == 0L && violations == 0) 0 else 1
}

fun experiment(): Int {
    require(args.size == 2)
    val project = File(args[0])
    require(project.isAbsolute && File(project, "run.sh").canExecute())
    val main = primary(args[1])
    val fixtures = (0..5).map { index -> main.copy(rows = main.rows.shuffled(Random(20261105 + index))) } + probes()
    val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    val log = Files.createTempFile("candidate-recall-service-", ".log").toFile()
    val builder = ProcessBuilder(File(project, "run.sh").absolutePath).directory(project)
        .redirectErrorStream(true).redirectOutput(log)
    builder.environment()["PORT"] = port.toString()
    val javaHome = requireNotNull(System.getenv("JAVA_HOME"))
    builder.environment()["PATH"] = "$javaHome/bin:${System.getenv("PATH") ?: ""}"
    val process = builder.start()
    val hook = Thread { stopService(process) }
    Runtime.getRuntime().addShutdownHook(hook)
    try {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            val base = "http://127.0.0.1:$port"
            val inputs = prepareLoad(base, fixtures)
            val readyAt = awaitReady(client, base, process)
            return report(sendLoad(client, inputs, readyAt))
        }
    } finally {
        stopService(process)
        Runtime.getRuntime().removeShutdownHook(hook)
        log.delete()
    }
}

val code = try { experiment() } catch (failure: Exception) {
    System.err.println("experiment error: ${failure.javaClass.simpleName}: ${failure.message}")
    2
}
exitProcess(code)
