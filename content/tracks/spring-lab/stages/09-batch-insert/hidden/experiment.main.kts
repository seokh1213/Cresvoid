@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.math.ceil
import kotlin.system.exitProcess

class Checks {
    var errors = 0
    var violations = 0
    var firstFailureRule = 0
        private set
    var firstFailureN = 0
        private set

    private fun failure(rule: Int, count: Int) {
        if (firstFailureRule == 0) {
            firstFailureRule = rule
            firstFailureN = count
        }
    }

    fun expect(condition: Boolean, rule: Int, count: Int) {
        if (!condition) {
            violations++
            failure(rule, count)
        }
    }

    fun decode(outcome: Outcome, status: Int): Map<String, Double> {
        val lines = outcome.body.lineSequence().filter { it.isNotBlank() }.toList()
        val values = lines.mapNotNull {
            val parts = it.split('=', limit = 2)
            if (parts.size != 2) null else parts[1].toDoubleOrNull()?.takeIf { number -> number.isFinite() }
                ?.let { number -> parts[0] to number }
        }.toList()
        val storedFields = setOf("rows", "ids", "ordinals", "bad_rows")
        val required = if (outcome.arrival.request.uri().path == "/rows") storedFields else storedFields +
            setOf("singles", "batches", "batch_rows", "max_managed", "elapsed_ms")
        if (outcome.status != status || outcome.error != null || values.isEmpty() || values.size != lines.size ||
            values.map { it.first }.distinct().size != values.size || !values.toMap().keys.containsAll(required)
        ) {
            errors++
            val count = outcome.arrival.request.uri().rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("count=") }?.substringAfter('=')?.toIntOrNull() ?: 0
            failure(1, count)
        }
        return values.toMap()
    }

    fun stored(values: Map<String, Double>, count: Int, requestCount: Int = count) {
        expect(values["rows"] == count.toDouble(), 2, requestCount)
        expect(values["ids"] == count.toDouble(), 3, requestCount)
        expect(values["ordinals"] == count.toDouble(), 4, requestCount)
        expect(values["bad_rows"] == 0.0, 5, requestCount)
    }

    fun inserted(values: Map<String, Double>, count: Int) {
        stored(values, count)
        expect(values["singles"] == 0.0, 6, count)
        expect(values["batch_rows"] == count.toDouble(), 7, count)
        expect(values["batches"]?.let { it in 1.0..ceil(count / 25.0) } == true, 8, count)
        expect(values["max_managed"]?.let { it in 1.0..200.0 } == true, 9, count)
        expect(values["elapsed_ms"]?.let { it >= 0 } == true, 10, count)
    }
}

data class ImportRun(val key: String, val count: Int)
data class MeasuredRun(val run: ImportRun, val values: Map<String, Double>, val latency: Double)

fun execute(program: File, scenario: String): Int {
    val checks = Checks()
    val counts = when (scenario) {
        "bulk-window" -> listOf(10_000, 10_000)
        "boundary-window" -> listOf(103, 17, 251)
        else -> error("unknown scenario: $scenario")
    }
    // ComposeStack owns cleanup, including partial startup and JVM shutdown.
    ComposeStack(File("compose.yaml"), "batch09", programEnvironment(program)).use { stack ->
        stack.up(90)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
            fun request(path: String, status: Int = 200): Map<String, Double> =
                checks.decode(runLoad(client, listOf(Arrival(0, post(base, path, "", Duration.ofSeconds(30))))).single(), status)
            fun query(run: ImportRun, expected: Int = run.count): Map<String, Double> {
                val response = runLoad(client, listOf(Arrival(0, get(base, "/rows?run=${run.key}&count=${run.count}", Duration.ofSeconds(30))))).single()
                val values = checks.decode(response, 200)
                checks.stored(values, expected, run.count)
                return values
            }

            val health = runLoad(client, listOf(Arrival(0, get(base, "/health", Duration.ofSeconds(30))))).single()
            checks.expect(health.status == 200 && health.error == null, 1, 0)
            checks.inserted(request("/insert?run=warm&count=100"), 100)
            val baseline = request("/observe?run=baseline&count=10000")
            checks.stored(baseline, 10_000)
            checks.expect(baseline["singles"] == 10_000.0, 6, 10_000)
            checks.expect(baseline["batches"] == 0.0, 8, 10_000)
            checks.expect(baseline["batch_rows"] == 0.0, 7, 10_000)
            checks.expect(baseline["elapsed_ms"]?.let { it >= 0 } == true, 10, 10_000)

            val runs = counts.mapIndexed { index, count -> ImportRun("sample-$index", count) }
            val outcomes = runLoad(client, runs.mapIndexed { index, run ->
                Arrival(index * 250L, post(base, "/insert?run=${run.key}&count=${run.count}", "", Duration.ofSeconds(30)))
            })
            val measured = runs.zip(outcomes).map { (run, outcome) ->
                val values = checks.decode(outcome, 200)
                checks.inserted(values, run.count)
                query(run)
                MeasuredRun(run, values, outcome.elapsedMillis)
            }.toMutableList()
            val failed = ImportRun("duplicate", 267)
            val rejected = request("/insert?run=${failed.key}&count=${failed.count}&duplicate=true", 409)
            checks.stored(rejected, 0, failed.count)
            checks.expect(rejected["elapsed_ms"]?.let { it >= 0 } == true, 10, failed.count)
            query(failed, 0)
            val reused = request("/insert?run=warm&count=100", 409)
            checks.stored(reused, 100)
            checks.expect(reused["elapsed_ms"]?.let { it >= 0 } == true, 10, 100)
            query(ImportRun("warm", 100))
            val recovery = ImportRun("recovery", 23)
            val recoveryOutcome = runLoad(client, listOf(Arrival(0, post(base, "/insert?run=${recovery.key}&count=23", "", Duration.ofSeconds(30))))).single()
            val recoveryValues = checks.decode(recoveryOutcome, 200)
            checks.inserted(recoveryValues, 23)
            measured.add(MeasuredRun(recovery, recoveryValues, recoveryOutcome.elapsedMillis))
            val storedRows = measured.sumOf { query(it.run)["rows"] ?: 0.0 }
            query(ImportRun("warm", 100))
            query(failed, 0)
            report(checks, baseline, measured, storedRows)
        }
    }
    return if (checks.errors == 0 && checks.violations == 0) 0 else 1
}

fun report(checks: Checks, baseline: Map<String, Double>, runs: List<MeasuredRun>, storedRows: Double) {
    fun sum(name: String) = runs.sumOf { it.values[name] ?: 0.0 }
    metric("baseline_calls", (baseline["singles"] ?: 0.0) + (baseline["batches"] ?: 0.0))
    metric("insert_calls", sum("singles") + sum("batches"))
    metric("batch_rows", sum("batch_rows"))
    metric("max_managed", runs.maxOf { it.values["max_managed"] ?: 0.0 })
    metric("stored_rows", storedRows)
    metric("errors", checks.errors)
    metric("violations", checks.violations)
    metric("import_p95_ms", percentile(runs.map { it.latency }, 0.95))
    metric("first_failure_rule", checks.firstFailureRule)
    metric("first_failure_n", checks.firstFailureN)
}

val result = try {
    require(args.size == 2 && File(args[0]).isAbsolute)
    execute(File(args[0]), args[1])
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(result)
