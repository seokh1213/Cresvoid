@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class Submission(val id: String, val amount: Int, val fault: String) {
    val eventId: String get() = "evt-$id"
    fun body(): String = "$id|$amount|$fault"
    fun event(): List<String> = listOf(eventId, id, amount.toString())
    fun response(): Pair<Int, String> = when (fault) {
        "rollback" -> 409 to "rolled-back"
        "commit-gap" -> 503 to "uncertain"
        else -> 202 to "accepted:$id"
    }
}

class Traffic(private val client: HttpClient, private val base: String) {
    var errors = 0
        private set

    fun check(condition: Boolean) {
        if (!condition) errors++
    }

    fun request(request: HttpRequest): Pair<Int, String> = try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        response.statusCode() to response.body()
    } catch (_: Exception) { 0 to "" }

    fun expect(request: HttpRequest, expected: Pair<Int, String>) = check(request(request) == expected)

    fun examples() {
        expect(get(base, "/health"), 200 to "ready")
        expect(get(base, "/state"), 200 to "")
        expect(get(base, "/relay/status"), 200 to "idle")
        expect(get(base, "/orders"), 405 to "method")
        expect(get(base, "/absent"), 404 to "missing")
        expect(post(base, "/orders", "bad|0|normal"), 400 to "invalid")
        expect(post(base, "/orders", "bad|1|unknown"), 400 to "invalid")
        expect(post(base, "/relay/start", "wrong"), 400 to "invalid")
    }

    fun submit(submissions: List<Submission>) {
        val schedule = submissions.mapIndexed { index, order ->
            Arrival(index * 1000L / 30, post(base, "/orders", order.body(), Duration.ofSeconds(30)))
        }
        runLoad(client, schedule).zip(submissions).forEach { (outcome, order) ->
            check(outcome.error == null && outcome.status to outcome.body == order.response())
        }
    }

    fun relay(crashEvent: String, exit: Int) {
        expect(post(base, "/relay/start", crashEvent), 200 to "started")
        val status = awaitTerminal()
        check(status == "done:$exit")
    }

    private fun awaitTerminal(): String {
        val deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos()
        do {
            val response = request(get(base, "/relay/status"))
            if (response.first == 200 && response.second.startsWith("done:")) return response.second
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        return "timeout"
    }

    fun drain(): Long {
        val deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos()
        do {
            val response = request(get(base, "/progress", Duration.ofSeconds(15)))
            val offsets = response.second.split('|').mapNotNull(String::toLongOrNull)
            if (response.first == 200 && offsets.size == 2 && offsets[0] == offsets[1]) return offsets[1]
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        errors++
        return -1
    }

    fun state(expected: String) = expect(get(base, "/state"), 200 to expected)
}

fun sql(stack: ComposeStack, query: String): List<List<String>> {
    val text = stack.exec("postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "lab", "-d", "lab",
        "-At", "-F", "|", "-c", query).trim()
    check(!text.contains("ERROR:") && !text.contains("FATAL:")) { "Database audit failed: $text" }
    return if (text.isEmpty()) emptyList() else text.lines().map { it.split('|') }
}

fun submissions(groups: Int): List<Submission> = listOf(Submission("anchor", 17, "normal")) +
    (0 until groups).flatMap { index -> listOf(
        Submission("order-$index", 100 + index, "normal"),
        Submission("gap-$index", 200 + index, "commit-gap"),
        Submission("void-$index", 300 + index, "rollback"),
    ) }

fun expectedState(committed: List<Submission>): String = committed.sortedBy { it.id }
    .joinToString("\n") { "${it.id}|${it.amount}" }

fun checkpoint(stack: ComposeStack, traffic: Traffic, committed: List<Submission>) {
    val expected = committed.map { listOf(it.id, it.amount.toString()) }.toSet()
    val orders = sql(stack, "SELECT order_id,amount FROM orders")
    traffic.check(orders.toSet() == expected && orders.size == expected.size)
    traffic.state(expectedState(committed))
}

fun report(stack: ComposeStack, traffic: Traffic, inputs: List<Submission>, end: Long): Boolean {
    val committed = inputs.filter { it.fault != "rollback" }
    checkpoint(stack, traffic, committed)
    val expected = committed.associateBy { it.eventId }
    val receipts = sql(stack, "SELECT event_id,order_id,amount FROM receipts ORDER BY offset_no")
    val published = sql(stack, "SELECT event_id,order_id,amount FROM published ORDER BY seq")
    val effects = sql(stack, "SELECT event_id,order_id,amount FROM effects ORDER BY seq")
    val pending = sql(stack, "SELECT count(*) FROM outbox WHERE NOT sent").single().single().toInt()
    val receiptCounts = receipts.groupingBy { it[0] }.eachCount()
    val effectCounts = effects.groupingBy { it[0] }.eachCount()
    val missingEvents = expected.keys.count { it !in receiptCounts }
    val ghosts = receipts.count { it[0] !in expected }
    val duplicates = effectCounts.values.sumOf { maxOf(0, it - 1) }
    val missingEffects = expected.keys.count { it !in effectCounts }
    val replayed = maxOf(0, receiptCounts.getOrDefault("evt-anchor", 0) - 1)
    traffic.check(receipts.size.toLong() == end)
    traffic.check(receipts.groupingBy { it }.eachCount() == published.groupingBy { it }.eachCount())
    for (row in receipts + effects) traffic.check(expected[row[0]]?.event() == row)
    traffic.check(effects.size == expected.size)
    traffic.check(effects.all { it[0] in receiptCounts })
    metric("attempted", inputs.size)
    metric("committed_orders", committed.size)
    metric("published_records", receipts.size)
    metric("missing_events", missingEvents)
    metric("ghost_events", ghosts)
    metric("duplicate_effects", duplicates)
    metric("missing_effects", missingEffects)
    metric("replayed_records", replayed)
    metric("pending_events", pending)
    metric("contract_errors", traffic.errors)
    return missingEvents == 0 && ghosts == 0 && duplicates == 0 && missingEffects == 0 &&
        replayed >= 1 && pending == 0 && traffic.errors == 0
}

fun experiment(program: File, groups: Int): Boolean {
    val port = freePort()
    val environment = programEnvironment(program) + mapOf("APP_PORT" to port.toString())
    ComposeStack(File("compose.yaml"), "backend-outbox", environment).use { stack ->
        stack.up(150)
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            val traffic = Traffic(client, "http://127.0.0.1:$port")
            traffic.examples()
            val inputs = submissions(groups)
            traffic.submit(inputs)
            checkpoint(stack, traffic, inputs.filter { it.fault != "rollback" })
            traffic.relay("evt-anchor", 73)
            traffic.drain()
            traffic.relay("", 0)
            val end = traffic.drain()
            return report(stack, traffic, inputs, end)
        }
    }
}

try {
    require(args.size == 2)
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    val groups = when (args[1]) {
        "weekday-orders" -> 8
        "weekend-orders" -> 12
        else -> error("Unknown scenario: ${args[1]}")
    }
    requireDocker()
    exitProcess(if (experiment(program, groups)) 0 else 1)
} catch (failure: Exception) {
    System.err.println("실험 오류: ${failure.message}")
    exitProcess(2)
}
