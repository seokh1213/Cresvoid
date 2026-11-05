@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class EventKey(val tenant: Int, val id: Int)
data class Event(val key: EventKey, val version: Int, val state: String, val amount: Long)
data class SummaryRequest(val tenant: Int, val state: String)
data class Phase(val outcomes: List<Outcome>, val wrong: Int)

fun sql(client: HttpClient, base: String, query: String): String {
    val request = HttpRequest.newBuilder(URI.create(base))
        .timeout(Duration.ofSeconds(30))
        .POST(HttpRequest.BodyPublishers.ofString(query))
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "ClickHouse: ${response.body().take(1200)}" }
    return response.body().trim()
}

fun events(perTenant: Int, version: Int): List<Event> = (1..3).flatMap { tenant ->
    (1..perTenant).map { id ->
        val currentState = if ((tenant + id) % 2 == 0) "active" else "hold"
        val oppositeState = if (currentState == "active") "hold" else "active"
        val amount = when (version) {
            10 -> 1000L + tenant * 100 + id
            20 -> 500L + tenant * 10 + id
            30 -> tenant * 100L + id * 3
            40 -> tenant * 10L + id
            else -> error("unknown version")
        }
        Event(EventKey(tenant, id), version, if (version == 30) currentState else oppositeState, amount)
    }
}

fun insert(client: HttpClient, base: String, rows: List<Event>) {
    val values = rows.joinToString(",") {
        "(${it.key.tenant},${it.key.id},${it.version},'${it.state}',${it.amount})"
    }
    sql(client, base, "INSERT INTO lab.events VALUES $values")
}

fun expected(rows: List<Event>, query: SummaryRequest): String {
    val latest = rows.groupBy { it.key }.values.map { versions -> versions.maxBy { it.version } }
    val matching = latest.filter {
        it.key.tenant == query.tenant && (query.state == "all" || it.state == query.state)
    }
    return "${matching.size}\t${matching.sumOf { it.amount }}\n"
}

fun measure(client: HttpClient, base: String, rows: List<Event>): Phase {
    val queries = (1..4).flatMap { tenant ->
        listOf("all", "active", "hold").map { SummaryRequest(tenant, it) }
    }
    val arrivals = steady(12, 2.0) { index ->
        val query = queries[index % queries.size]
        get(base, "/summary?tenant=${query.tenant}&state=${query.state}", Duration.ofSeconds(10))
    }
    val outcomes = runLoad(client, arrivals)
    val wrong = outcomes.withIndex().count { (index, outcome) ->
        outcome.status != 200 || outcome.error != null ||
            outcome.body != expected(rows, queries[index % queries.size])
    }
    return Phase(outcomes, wrong)
}

fun count(client: HttpClient, base: String, suffix: String = ""): Long =
    sql(client, base, "SELECT count() FROM lab.events $suffix FORMAT TabSeparated").toLong()

fun createTable(client: HttpClient, base: String) {
    sql(client, base, "CREATE DATABASE lab")
    sql(client, base, """
        CREATE TABLE lab.events (
            tenant UInt32, id UInt32, version UInt64, state String, amount Int64
        ) ENGINE = ReplacingMergeTree(version) ORDER BY (tenant, id)
    """.trimIndent())
    sql(client, base, "SYSTEM STOP MERGES lab.events")
}

fun evaluate(client: HttpClient, clickHouse: String, base: String, perTenant: Int): Boolean {
    val initial = listOf(10, 30, 20, 30).flatMap { events(perTenant, it) }
    listOf(10, 30, 20, 30).forEach { insert(client, clickHouse, events(perTenant, it)) }
    val keys = perTenant * 3L
    val rawBefore = count(client, clickHouse)
    val finalBefore = count(client, clickHouse, "FINAL")
    check(rawBefore == 4 * keys && finalBefore == keys) { "initial observation did not form" }
    val before = measure(client, base, initial)

    sql(client, clickHouse, "SYSTEM START MERGES lab.events")
    sql(client, clickHouse, "OPTIMIZE TABLE lab.events FINAL")
    val rawAfter = count(client, clickHouse)
    check(rawAfter == keys) { "physical merge did not form" }
    val after = measure(client, base, initial)

    sql(client, clickHouse, "SYSTEM STOP MERGES lab.events")
    val fresh = events(perTenant, 40)
    val replay = events(perTenant, 20)
    insert(client, clickHouse, fresh)
    insert(client, clickHouse, replay)
    val rawRefresh = count(client, clickHouse)
    check(rawRefresh == 3 * keys) { "refresh observation did not form" }
    val refresh = measure(client, base, initial + fresh + replay)
    val outcomes = before.outcomes + after.outcomes + refresh.outcomes
    val errors = outcomes.count { it.status != 200 || it.error != null }

    metric("requests", outcomes.size)
    metric("http_errors", errors)
    metric("wrong_before", before.wrong)
    metric("wrong_after", after.wrong)
    metric("wrong_refresh", refresh.wrong)
    metric("raw_before", rawBefore)
    metric("final_before", finalBefore)
    metric("raw_after", rawAfter)
    metric("raw_refresh", rawRefresh)
    metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
    return outcomes.size == 72 && errors == 0 && listOf(before, after, refresh).all { it.wrong == 0 }
}

fun experiment(): Boolean {
    require(args.size == 2) { "expected program directory and scenario" }
    val programDir = File(args[0])
    require(programDir.isAbsolute && File(programDir, "run.sh").canExecute())
    val perTenant = when (args[1]) {
        "daily-summary" -> 6
        "rolling-summary" -> 17
        else -> error("unknown scenario")
    }
    requireDocker()
    return ComposeStack(File("compose.yaml"), "olap-replacing").use { stack ->
        stack.up()
        val clickHouse = "http://127.0.0.1:${stack.port("clickhouse", 8123)}"
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
        createTable(client, clickHouse)
        val port = freePort()
        val base = "http://127.0.0.1:$port"
        var program: Program? = null
        try {
            program = Program(programDir, mapOf("PORT" to "$port", "CLICKHOUSE_URL" to clickHouse), base)
            evaluate(client, clickHouse, base, perTenant)
        } finally {
            program?.close()
            client.close()
        }
    }
}

val code = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.message}")
    2
}
exitProcess(code)
