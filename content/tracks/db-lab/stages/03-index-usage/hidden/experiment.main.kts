@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import kotlin.system.exitProcess

data class Probe(val kind: String, val input: Map<String, String>, val expected: List<Int>, val budget: Long)
data class StatementRead(val token: String, val rows: Long, val error: Int)

fun sql(stack: ComposeStack, query: String): String = stack.exec(
    "mysql", "mysql", "-uroot", "--batch", "--skip-column-names", "-e", query,
).also { check(!it.startsWith("ERROR")) { it.take(300) } }

fun seed(stack: ComposeStack) {
    sql(stack, File("../tools/catalog.sql").readText())
    check(sql(stack, "SELECT COUNT(*) FROM lab.products").trim() == "1000000")
}

fun startApp(stack: ComposeStack, program: File) {
    val process = ProcessBuilder(
        "docker", "compose", "-p", stack.project, "-f", File("compose.yaml").absolutePath,
        "up", "-d", "--wait", "--wait-timeout", "120", "--scale", "app=1", "app",
    ).redirectErrorStream(true).apply {
        environment().putAll(LAB_IMAGES + programEnvironment(program))
    }.start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "App startup: ${output.takeLast(1500)}" }
}

fun probes(): List<Probe> {
    val dayOffsets = listOf(-1, 0, 120, 730, 999, 1000)
    val skus = listOf("000000004321", "4321", "000000000001", "000001000000", "000001000001", "4321 ")
    val categories = listOf(0, 17, 199, 42, 200, -1)
    val prices = listOf(0, 25000, 70000, 90000, 99999, 100000)
    val cursors = listOf(500000, 225000, 370000, 890000, 999999, 0)
    val priceOrder = (1..1_000_000).sortedWith(compareBy<Int> { it % 100000 }.thenBy { it })
    return (0..5).flatMap { index ->
        val day = dayOffsets[index]
        val category = categories[index]
        val sku = skus[index]
        val price = prices[index]
        val cursor = cursors[index]
        val offset = priceOrder.indexOfFirst { it % 100000 > price || (it % 100000 == price && it > cursor) }
            .let { if (it < 0) priceOrder.size else it }
        listOf(
            Probe("day", mapOf("date" to LocalDate.of(2024, 1, 1).plusDays(day.toLong()).toString()),
                (1..1_000_000).asSequence().filter { it % 1000 == day }.take(25).toList(), 64),
            Probe("sku", mapOf("sku" to sku), (1..1_000_000).asSequence().filter {
                (if (it == 999999) "4321" else it.toString().padStart(12, '0')) == sku
            }.toList(), 4),
            Probe("category", mapOf("category" to category.toString()),
                (1..1_000_000).asSequence().filter { it % 200 == category }.take(25).toList(), 64),
            Probe("page", mapOf("price" to "$price", "id" to "$cursor", "offset" to "$offset"),
                priceOrder.drop(offset).take(25), 64),
        )
    }
}

fun statementReads(stack: ComposeStack): List<StatementRead> = sql(stack, """
    SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(SQL_TEXT,'*/',1),':',-1), ROWS_EXAMINED, MYSQL_ERRNO
    FROM performance_schema.events_statements_history_long
    WHERE SQL_TEXT LIKE '/*lab03:%'
""".trimIndent()).lineSequence().filter { it.isNotBlank() }.map { line ->
    val fields = line.split('\t')
    check(fields.size == 3) { "Invalid statement event: ${line.take(200)}" }
    StatementRead(fields[0], fields[1].toLong(), fields[2].toInt())
}.toList()

fun inspect(stack: ComposeStack, base: String): Boolean {
    val probes = probes()
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
        check(client.send(get(base, "/health"), HttpResponse.BodyHandlers.ofString()).statusCode() == 200)
        val arrivals = steady(2, 12.0) { index ->
            val probe = probes[index]
            val query = (probe.input + mapOf("kind" to probe.kind, "token" to "r$index")).entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
            }
            get(base, "/query?$query", Duration.ofSeconds(60))
        }
        val outcomes = runLoad(client, arrivals)
        check(outcomes.none { it.error != null }) { "HTTP transport error or request timeout" }
        val reads = statementReads(stack).groupBy { it.token }
        val errors = outcomes.count { it.status != 200 || it.error != null }
        val mismatches = outcomes.withIndex().count { (index, result) ->
            result.status == 200 && result.body != probes[index].expected.joinToString(",")
        }
        val gaps = probes.indices.count { index ->
            val events = reads["r$index"].orEmpty()
            events.size != 1 || events.any { it.error != 0 }
        }
        val excess = probes.indices.count { index -> reads["r$index"].orEmpty().sumOf { it.rows } > probes[index].budget }
        metric("requests", outcomes.size)
        metric("http_errors", errors)
        metric("result_mismatches", mismatches)
        metric("statement_gaps", gaps)
        for (kind in listOf("day", "sku", "category", "page")) {
            val maximum = probes.indices.filter { probes[it].kind == kind }
                .maxOf { index -> reads["r$index"].orEmpty().sumOf { it.rows } }
            metric("${kind}_max_rows", maximum)
        }
        metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
        return outcomes.size == 24 && errors == 0 && mismatches == 0 && gaps == 0 && excess == 0
    }
}

try {
    require(args.size == 2 && args[1] == "catalog-round")
    val program = File(args[0]).canonicalFile
    require(program.isAbsolute && File(program, "build/app.jar").isFile)
    requireDocker()
    val passed = ComposeStack(File("compose.yaml"), "db-index-usage", programEnvironment(program)).use { stack ->
        stack.up(180)
        seed(stack)
        startApp(stack, program)
        inspect(stack, "http://127.0.0.1:${stack.port("app", 8080)}")
    }
    exitProcess(if (passed) 0 else 1)
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.message?.take(1800)}")
    exitProcess(2)
}
