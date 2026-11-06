@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

data class Expected(val rows: Int, val exception: String, val outer: Int, val inner: Int, val transactional: Int)

val expected = mapOf(
    "self" to Expected(1, "IllegalStateException", 0, 0, 0),
    "private" to Expected(1, "IllegalStateException", 0, 0, 0),
    "final" to Expected(0, "AopConfigException", 0, 0, 0),
    "checked" to Expected(1, "IOException", 0, 0, 1),
    "required" to Expected(0, "UnexpectedRollbackException", 0, 0, 0),
    "nested" to Expected(2, "none", 2, 0, 2),
)
val keys = listOf("case", "rows", "exception", "outer_rows", "inner_rows", "transactional_rows")
val countKeys = listOf("rows", "outer_rows", "inner_rows", "transactional_rows")

data class Report(val fields: Map<String, String>, val valid: Boolean) {
    fun number(key: String): Int? = fields[key]?.toIntOrNull()
}

fun parse(body: String, case: String): Report {
    val lines = body.removeSuffix("\n").split('\n')
    val fields = lines.associate { it.substringBefore('=') to it.substringAfter('=', "") }
    val valid = lines.map { it.substringBefore('=') } == keys &&
        fields["case"] == case &&
        fields["exception"]?.matches(Regex("[A-Za-z][A-Za-z0-9]*")) == true &&
        countKeys.all { key -> fields[key]?.matches(Regex("0|[1-9][0-9]*")) == true && fields[key]?.toIntOrNull() != null }
    return Report(fields, valid)
}

fun evaluate(cases: List<String>, outcomes: List<Outcome>): Int {
    var rowsWrong = 0
    var exceptionsWrong = 0
    var transactionsWrong = 0
    var shapeWrong = 0
    val totals = cases.associateWith { 0L }.toMutableMap()
    for (outcome in outcomes) {
        val case = outcome.arrival.request.uri().path.substringAfterLast('/')
        val report = parse(outcome.body, case)
        val target = expected.getValue(case)
        if (!report.valid) shapeWrong++
        if (report.number("rows") != target.rows || report.number("outer_rows") != target.outer ||
            report.number("inner_rows") != target.inner) rowsWrong++
        if (report.fields["exception"] != target.exception) exceptionsWrong++
        if (report.number("transactional_rows") != target.transactional) transactionsWrong++
        totals[case] = totals.getValue(case) + (report.number("rows") ?: 0)
    }
    val failures = outcomes.count { it.status != 200 || it.error != null }
    val completed = outcomes.count { it.status == 200 && it.error == null }
    cases.forEach { case -> metric("${case}_rows", totals.getValue(case)) }
    metric("http_failures", failures)
    metric("row_mismatches", rowsWrong)
    metric("exception_mismatches", exceptionsWrong)
    metric("transaction_mismatches", transactionsWrong)
    metric("shape_mismatches", shapeWrong)
    metric("completed", completed)
    metric("p95_ms", percentile(outcomes.map { it.elapsedMillis }, 0.95))
    return if (completed == 6 && outcomes.size == 6 && failures + rowsWrong + exceptionsWrong + transactionsWrong + shapeWrong == 0) 0 else 1
}

fun experiment(): Int {
    require(args.size == 2) { "usage: experiment.main.kts <absolute-program-dir> <scenario>" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile) { "built absolute program directory required" }
    val cases = when (args[1]) {
        "entry-samples" -> listOf("self", "private", "final")
        "boundary-samples" -> listOf("checked", "required", "nested")
        else -> error("unknown scenario")
    }
    requireDocker()
    ComposeStack(File("compose.yaml"), "spring-proxy", programEnvironment(program)).use { stack ->
        stack.up(60)
        val port = stack.port("app", 8080)
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
            val arrivals = steady(2, 3.0) { index ->
                post("http://127.0.0.1:$port", "/observe/${cases[index % cases.size]}", "", Duration.ofSeconds(10))
            }
            return evaluate(cases, runLoad(client, arrivals))
        }
    }
}

val result = try {
    experiment()
} catch (error: Exception) {
    System.err.println("experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
