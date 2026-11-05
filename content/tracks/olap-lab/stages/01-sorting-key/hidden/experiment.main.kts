@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class Probe(val kind: String, val tenant: Int, val day: Int, val type: Int = 2) {
    fun path(): String = "/query?table=a&kind=$kind&tenant=$tenant&day=$day&type=$type"
}
data class Reading(val rows: Long, val marks: Long)

class Violations {
    var count = 0
        private set
    var flags = 0
        private set

    fun record(invalid: Boolean, category: Int) {
        if (!invalid) return
        count++
        flags = flags or category
    }
}

fun sql(client: HttpClient, base: String, query: String): String {
    val request = HttpRequest.newBuilder(URI.create(base)).timeout(Duration.ofSeconds(90))
        .POST(HttpRequest.BodyPublishers.ofString(query)).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "measurement SQL failed: ${response.body().take(300)}" }
    return response.body().trim()
}

fun expected(probe: Probe): Long {
    var sum = 0L
    for (id in 0 until 1048576) {
        val tenant = id % 64
        val day = id / 64 % 32
        val type = id / 2048 % 4
        val matches = when (probe.kind) {
            "tenant" -> tenant == probe.tenant
            "window" -> tenant == probe.tenant && day == probe.day
            "day" -> day == probe.day
            "type" -> type == probe.type
            else -> error("unknown probe")
        }
        if (matches) sum += id % 97 + 1L
    }
    return sum
}

fun probes(scenario: String): List<Probe> {
    val tenants = when (scenario) {
        "first-pass" -> listOf(3, 17, 38, 59)
        "second-pass" -> listOf(0, 21, 42, 63)
        else -> error("unknown scenario")
    }
    return tenants.flatMapIndexed { index, tenant ->
        listOf(Probe("tenant", tenant, 0), Probe("window", tenant, index * 9 + 2))
    }
}

fun readings(client: HttpClient, base: String, ids: List<String>): Map<String, Reading> {
    sql(client, base, "SYSTEM FLUSH LOGS")
    if (ids.isEmpty()) return emptyMap()
    val quoted = ids.joinToString(",") { "'$it'" }
    val rows = sql(client, base, """
        SELECT query_id, any(read_rows), any(ProfileEvents['SelectedMarks']) FROM system.query_log
        WHERE type='QueryFinish' AND query_id IN ($quoted)
        GROUP BY query_id HAVING count() = 1 FORMAT TSV
    """.trimIndent())
    if (rows.isEmpty()) return emptyMap()
    return rows.lines().associate { line ->
        val fields = line.split('\t')
        fields[0] to Reading(fields[1].toLong(), fields[2].toLong())
    }
}

fun measure(client: HttpClient, base: String, database: String, workload: List<Probe>): Int {
    val timeout = Duration.ofSeconds(90)
    val prepared = client.send(post(base, "/prepare", "", timeout), HttpResponse.BodyHandlers.ofString())
    check(prepared.statusCode() == 200) { "fixture preparation failed" }
    val observation = client.send(get(base, "/observe", timeout), HttpResponse.BodyHandlers.ofString())
    val storage = client.send(get(base, "/storage", timeout), HttpResponse.BodyHandlers.ofString())
    val outcomes = runLoad(client, steady(2, 4.0) { get(base, workload[it].path(), timeout) })
    val errors = outcomes.count { it.status != 200 || it.error != null } +
        listOf(observation, storage).count { it.statusCode() != 200 }
    val parsed = outcomes.map { outcome -> outcome.body.trim().split('\t') }
    val validIds = parsed.mapNotNull { fields ->
        fields.firstOrNull()?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }
    }
    val measured = readings(client, database, validIds)
    val violations = Violations()
    for (index in outcomes.indices) {
        val fields = parsed[index]
        violations.record(fields.size != 2 || fields.getOrNull(1)?.toLongOrNull() != expected(workload[index]), 1)
        violations.record(measured[fields.firstOrNull()]?.let { it.rows > 0 && it.marks > 0 } != true, 2)
    }
    violations.record(validIds.toSet().size != workload.size, 2)
    val observationRows = observation.body().trim().lines().map { it.split('\t') }
    val observed = mutableMapOf<String, Long>()
    val expectedPairs = setOf("a", "b", "c").flatMap { table ->
        listOf("tenant", "day", "type").map { "$table:$it" }
    }.toSet()
    for (fields in observationRows) {
        val key = "${fields.getOrNull(0)}:${fields.getOrNull(1)}"
        val kind = fields.getOrNull(1)
        if (fields.size != 5 || key !in expectedPairs) { violations.record(true, 4); continue }
        val probe = Probe(kind!!, 17, 7)
        val rows = fields[3].toLongOrNull() ?: 0
        val marks = fields[4].toLongOrNull() ?: 0
        violations.record(fields[2].toLongOrNull() != expected(probe) || rows <= 0 || marks <= 0, 4)
        violations.record(observed.put(key, rows) != null, 4)
    }
    violations.record(observed.keys != expectedPairs, 4)
    val ratios = mutableMapOf<String, Double>()
    val actualStorage = sql(client, database, """
        SELECT table, sum(rows), sum(data_compressed_bytes), sum(data_uncompressed_bytes), count()
        FROM system.parts WHERE database='sorting_lab' AND active GROUP BY table ORDER BY table FORMAT TSV
    """.trimIndent())
    violations.record(storage.body().trim() != actualStorage, 8)
    for (line in actualStorage.lines().filter { it.isNotBlank() }) {
        val fields = line.split('\t')
        check(fields.size == 5)
        violations.record(fields[1].toLong() != 1048576L, 16)
        violations.record(fields[0] == "a" && fields[4].toLong() > 2, 32)
        val compressed = fields[2].toDouble()
        val uncompressed = fields[3].toDouble()
        violations.record(compressed <= 0 || uncompressed <= 0, 64)
        ratios[fields[0]] = if (uncompressed > 0) compressed / uncompressed else 0.0
    }
    violations.record(ratios.keys != setOf("a", "b", "c"), 16)
    val totalRows = measured.values.sumOf { it.rows }
    metric("request_errors", errors)
    metric("wrong_results", violations.count)
    metric("tuned_read_rows", totalRows)
    metric("failure_flags", violations.flags)
    for (table in listOf("a", "b", "c")) metric("${table}_tenant_rows", observed["$table:tenant"] ?: 0)
    for (table in listOf("a", "b", "c")) metric("${table}_compression", ratios[table] ?: 0.0)
    return if (errors == 0 && violations.count == 0 && totalRows in 1..160000) 0 else 1
}

fun experiment(): Int {
    require(args.size == 2)
    val directory = File(args[0])
    require(directory.isAbsolute && directory.isDirectory)
    val workload = probes(args[1])
    requireDocker()
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
        ComposeStack(File("compose.yaml"), "olap-sorting-key").use { stack ->
            stack.up(180)
            val database = "http://127.0.0.1:${stack.port("clickhouse", 8123)}"
            val port = freePort()
            val base = "http://127.0.0.1:$port"
            Program(directory, mapOf("PORT" to "$port", "CLICKHOUSE_URL" to database), base).use {
                return measure(client, base, database, workload)
            }
        }
    }
}

val code = try { experiment() } catch (error: Exception) {
    System.err.println("experiment error: ${error.message}")
    2
}
exitProcess(code)
