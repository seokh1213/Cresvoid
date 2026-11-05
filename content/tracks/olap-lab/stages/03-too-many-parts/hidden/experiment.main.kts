@file:Import("../../../../../lab-env/docker.main.kts")
@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.abs
import kotlin.system.exitProcess

data class StoredEvent(val id: Long, val value: Long)
data class Snapshot(val parts: Int, val rows: List<StoredEvent>)

class Database(private val origin: String, private val client: HttpClient) {
    fun query(sql: String): String {
        val response = client.send(
            HttpRequest.newBuilder(URI.create("$origin/?wait_end_of_query=1"))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(sql)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        check(response.statusCode() == 200) { "ClickHouse query failed: ${response.body().take(600)}" }
        return response.body().trim()
    }

    fun prepare() {
        query("""
            CREATE TABLE default.events (id UInt64, value UInt64)
            ENGINE = MergeTree ORDER BY id
            SETTINGS parts_to_delay_insert=48, parts_to_throw_insert=48,
                max_parts_in_total=96, max_avg_part_size_for_too_many_parts=1073741824
        """.trimIndent())
        query("SYSTEM STOP MERGES default.events")
    }

    fun snapshot(): Snapshot {
        val parts = query("""
            SELECT count() FROM system.parts
            WHERE database='default' AND table='events' AND active
            FORMAT TabSeparated
        """.trimIndent()).toInt()
        val body = query("SELECT id, value FROM default.events FORMAT TabSeparated")
        val rows = if (body.isEmpty()) emptyList() else body.lines().map { line ->
            val fields = line.split('\t')
            check(fields.size == 2) { "Invalid ClickHouse row" }
            StoredEvent(fields[0].toLong(), fields[1].toLong())
        }
        return Snapshot(parts, rows)
    }
}

class Measurements {
    val outcomes = mutableListOf<Outcome>()
    var flushErrors = 0
    var peakParts = 0
    var rowMismatches = 0
    var repeatPartsGrowth = 0
    var lastRows = emptyList<StoredEvent>()

    fun observe(snapshot: Snapshot, expected: List<StoredEvent>) {
        peakParts = maxOf(peakParts, snapshot.parts)
        val actualCounts = snapshot.rows.groupingBy { it }.eachCount()
        val expectedCounts = expected.groupingBy { it }.eachCount()
        rowMismatches += (actualCounts.keys + expectedCounts.keys).sumOf { event ->
            abs(actualCounts.getOrDefault(event, 0) - expectedCounts.getOrDefault(event, 0))
        }
        lastRows = snapshot.rows
    }

    fun report(): Boolean {
        val requestErrors = outcomes.count { it.status != 202 || it.body != "accepted" }
        val duplicates = lastRows.size - lastRows.map { it.id }.toSet().size
        metric("requests", outcomes.size)
        metric("request_errors", requestErrors)
        metric("flush_errors", flushErrors)
        metric("peak_parts", peakParts)
        metric("stored_rows", lastRows.size)
        metric("row_mismatches", rowMismatches)
        metric("duplicate_rows", duplicates)
        metric("repeat_parts_growth", repeatPartsGrowth)
        metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
        return outcomes.size == 103 && requestErrors == 0 && flushErrors == 0 && peakParts <= 8 &&
            lastRows.size == 155 && rowMismatches == 0 && duplicates == 0 && repeatPartsGrowth == 0
    }
}

fun requests(): List<List<StoredEvent>> {
    var nextId = 1L
    return List(103) { index ->
        List(if (index % 2 == 0) 2 else 1) {
            val id = nextId++
            StoredEvent(id, id * 17 + 3)
        }
    }
}

fun flush(client: HttpClient, base: String, measurements: Measurements) {
    val outcome = runLoad(client, listOf(Arrival(0, post(base, "/flush", "", Duration.ofSeconds(15))))).single()
    if (outcome.status != 200 || outcome.body != "flushed") measurements.flushErrors++
}

fun exercise(client: HttpClient, base: String, database: Database, scenario: String): Boolean {
    val batches = requests()
    val segments = when (scenario) {
        "weekday-flow" -> listOf(batches)
        "evening-flow" -> listOf(batches.take(60), batches.drop(60))
        else -> error("Unknown scenario: $scenario")
    }
    val measurements = Measurements()
    val expected = mutableListOf<StoredEvent>()
    for (segment in segments) {
        val arrivals = steady(40, segment.size / 40.0) { index ->
            val body = segment[index].joinToString("\n", postfix = "\n") { "${it.id}\t${it.value}" }
            post(base, "/events", body, Duration.ofSeconds(15))
        }
        measurements.outcomes += runLoad(client, arrivals)
        expected.addAll(segment.flatten())
        flush(client, base, measurements)
        val first = database.snapshot()
        measurements.observe(first, expected)
        flush(client, base, measurements)
        val second = database.snapshot()
        measurements.repeatPartsGrowth += abs(second.parts - first.parts)
        measurements.observe(second, expected)
    }
    return measurements.report()
}

fun experiment(): Boolean {
    require(args.size == 2) { "Expected program directory and scenario" }
    val programDir = File(args[0])
    require(programDir.isAbsolute && File(programDir, "run.sh").canExecute())
    require(args[1] in setOf("weekday-flow", "evening-flow"))
    requireDocker()
    ComposeStack(File("compose.yaml"), "olap-parts").use { stack ->
        stack.up()
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
            val clickHouseUrl = "http://127.0.0.1:${stack.port("clickhouse", 8123)}"
            val database = Database(clickHouseUrl, client)
            database.prepare()
            val port = freePort()
            val base = "http://127.0.0.1:$port"
            val environment = mapOf(
                "PORT" to port.toString(), "CLICKHOUSE_URL" to clickHouseUrl,
                "CLICKHOUSE_TABLE" to "default.events",
            )
            Program(programDir, environment, base).use {
                return exercise(client, base, database, args[1])
            }
        }
    }
}

val result = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.message}")
    EXPERIMENT_ERROR
}
exitProcess(result)
