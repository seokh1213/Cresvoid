@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class InputEvent(val id: String, val entity: String, val version: Int, val value: Int, val delta: Int) {
    fun body() = "$id|$entity|$version|$value|$delta"
}

data class EntityState(val version: Int, val value: Int, val total: Int)
data class Effect(val event: InputEvent, val before: Int, val after: Int)
data class Audit(val duplicates: Int, val missing: Int, val regressions: Int, val errors: Int, val errorMask: Int)
data class Phases(val checkpointErrors: Int, val replayed: Int)

class Traffic(private val client: HttpClient, private val base: String) {
    var errors = 0
        private set
    var errorMask = 0
        private set
    var published = 0

    fun recordError(mask: Int, count: Int = 1) {
        errors += count
        if (count > 0) errorMask = errorMask or mask
    }

    fun request(request: HttpRequest): Pair<Int, String> = try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        response.statusCode() to response.body()
    } catch (_: Exception) { 0 to "" }

    fun expect(request: HttpRequest, status: Int, body: String) {
        if (request(request) != status to body) recordError(1)
    }

    fun start(crash: String = "") = expect(post(base, "/control/start", crash), 200, "started")

    fun publish(events: List<InputEvent>) {
        val arrivals = events.mapIndexed { index, event ->
            Arrival(index * 1000L / 60, post(base, "/events", event.body(), Duration.ofSeconds(20)))
        }
        val results = runLoad(client, arrivals)
        recordError(1, results.zip(events).count { (result, event) ->
            result.status != 202 || result.body != "queued:${event.id}" || result.error != null
        })
        published += events.size
    }

    fun awaitStatus(status: String): Boolean = await { request(get(base, "/status")) == 200 to status }

    fun drain(): Boolean {
        val done = await { request(get(base, "/progress")) == 200 to "$published|$published" }
        if (!done) recordError(2)
        return done
    }

    fun examples() {
        expect(get(base, "/health"), 200, "ready")
        expect(get(base, "/events"), 405, "method")
        expect(post(base, "/events", ""), 400, "invalid")
        expect(post(base, "/events", "bad|item-a|0|1|1"), 400, "invalid")
        expect(get(base, "/absent"), 404, "missing")
        expect(get(base, "/state"), 200, "")
        expect(get(base, "/status"), 200, "idle")
    }

    fun state(text: String) = expect(get(base, "/state"), 200, text)

    private fun await(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos()
        do {
            if (condition()) return true
            Thread.sleep(150)
        } while (System.nanoTime() < deadline)
        return false
    }
}

fun sql(stack: ComposeStack, query: String): List<List<String>> {
    val text = stack.exec("postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "lab", "-d", "lab", "-At", "-F", "|", "-c", query).trim()
    check(!text.contains("ERROR:") && !text.contains("FATAL:")) { "DB audit failed: $text" }
    return if (text.isEmpty()) emptyList() else text.lines().map { it.split('|') }
}

fun states(stack: ComposeStack): Map<String, EntityState> =
    sql(stack, "SELECT entity_id,version,value,total FROM entities ORDER BY entity_id").associate {
        it[0] to EntityState(it[1].toInt(), it[2].toInt(), it[3].toInt())
    }

fun stateText(states: Map<String, EntityState>) = states.toSortedMap().entries.joinToString("\n") { (id, state) ->
    "$id|${state.version}|${state.value}|${state.total}"
}

fun reference(events: List<InputEvent>): Map<String, EntityState> = events.distinctBy { it.id }.groupBy { it.entity }.mapValues { (_, group) ->
    val latest = group.maxBy { it.version }
    EntityState(latest.version, latest.value, group.sumOf { it.delta })
}

fun mismatches(expected: Map<String, EntityState>, actual: Map<String, EntityState>): Int =
    (expected.keys + actual.keys).count { expected[it] != actual[it] }

fun effects(stack: ComposeStack): List<Effect> =
    sql(stack, "SELECT event_id,entity_id,version,value,delta,previous_version,after_version FROM effects ORDER BY seq").map {
        Effect(InputEvent(it[0], it[1], it[2].toInt(), it[3].toInt(), it[4].toInt()), it[5].toInt(), it[6].toInt())
    }

fun audit(events: List<InputEvent>, effects: List<Effect>, actual: Map<String, EntityState>): Audit {
    val expected = events.associateBy { it.id }
    val counts = effects.groupingBy { it.event.id }.eachCount()
    val reconstructed = mutableMapOf<String, EntityState>()
    var errors = 0
    var errorMask = 0
    fun recordError(mask: Int, count: Int = 1) {
        errors += count
        if (count > 0) errorMask = errorMask or mask
    }
    for (effect in effects) {
        val event = effect.event
        val previous = reconstructed[event.entity] ?: EntityState(0, 0, 0)
        if (expected[event.id] != event) recordError(16)
        if (previous.version != effect.before) recordError(32)
        if (effect.after != maxOf(previous.version, event.version)) recordError(64)
        reconstructed[event.entity] = EntityState(
            effect.after, if (effect.after == event.version) event.value else previous.value, previous.total + event.delta,
        )
    }
    recordError(128, mismatches(reconstructed, actual))
    return Audit(counts.values.sumOf { maxOf(0, it - 1) }, expected.keys.count { it !in counts },
        effects.count { it.after < it.before }, errors, errorMask)
}

fun ordinaryEvents(count: Int): List<InputEvent> = (0 until count).flatMap { entity ->
    (1..6).map { version ->
        InputEvent("event-$entity-$version", "item-$entity", version, 100 * entity + 7 * version, (entity + 1) * (version - 3))
    }
}

fun exercise(stack: ComposeStack, traffic: Traffic, count: Int): Boolean {
    traffic.examples()
    val anchor = InputEvent("anchor-event", "anchor", 1, 17, 11)
    traffic.publish(listOf(anchor))
    traffic.start(anchor.id)
    if (!traffic.awaitStatus("dead:73")) traffic.recordError(4)
    val first = effects(stack)
    if (first.none { it.event == anchor }) traffic.recordError(8)
    // The first record is the only record in Kafka, so both initial offsets are known independently.
    val base = "http://127.0.0.1:${stack.port("app", 8080)}"
    if (traffic.request(get(base, "/progress")) != 200 to "0|1") traffic.recordError(8)
    traffic.start()
    traffic.drain()
    val replayed = sql(stack, "SELECT count(*) FROM deliveries WHERE event_id='anchor-event'").single().single().toInt() - 1
    val ordinary = ordinaryEvents(count)
    val newest = ordinary.filter { it.version == 6 }
    traffic.publish(newest.flatMap { listOf(it, it) })
    traffic.drain()
    val checkpointErrors = mismatches(reference(listOf(anchor) + newest), states(stack))
    val older = listOf(2, 5, 1, 4, 3).flatMap { version -> ordinary.filter { it.version == version }.reversed() }
    traffic.publish(older.flatMap { listOf(it, it, it) } + List(3) { anchor })
    traffic.drain()
    return report(stack, traffic, listOf(anchor) + ordinary, Phases(checkpointErrors, replayed))
}

fun report(stack: ComposeStack, traffic: Traffic, events: List<InputEvent>, phases: Phases): Boolean {
    val finalState = states(stack)
    val rows = effects(stack)
    val audit = audit(events, rows, finalState)
    traffic.state(stateText(finalState))
    val stateErrors = phases.checkpointErrors + mismatches(reference(events), finalState)
    val errors = traffic.errors + audit.errors
    metric("published", traffic.published)
    metric("unique_events", events.size)
    metric("effect_rows", rows.size)
    metric("duplicate_effects", audit.duplicates)
    metric("missing_effects", audit.missing)
    metric("version_regressions", audit.regressions)
    metric("state_mismatches", stateErrors)
    metric("replayed_records", phases.replayed)
    metric("contract_errors", errors)
    metric("contract_error_mask", traffic.errorMask or audit.errorMask)
    return rows.size == events.size && audit.duplicates == 0 && audit.missing == 0 && audit.regressions == 0 &&
        stateErrors == 0 && phases.replayed >= 1 && errors == 0
}

fun experiment(program: File, count: Int): Boolean {
    val port = freePort()
    val environment = programEnvironment(program) + mapOf("APP_PORT" to port.toString())
    ComposeStack(File("compose.yaml"), "backend-idempotent-consumer", environment).use { stack ->
        stack.up(150)
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            return exercise(stack, Traffic(client, "http://127.0.0.1:$port"), count)
        }
    }
}

try {
    require(args.size == 2)
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    val count = when (args[1]) {
        "morning-traffic" -> 6
        "evening-traffic" -> 9
        else -> error("Unknown scenario: ${args[1]}")
    }
    requireDocker()
    exitProcess(if (experiment(program, count)) 0 else 1)
} catch (failure: Exception) {
    System.err.println("실험 오류: ${failure.message}")
    exitProcess(2)
}
