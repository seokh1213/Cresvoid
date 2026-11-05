@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

data class FileCase(val tasks: Int, val dates: Int, val rows: Int, val strategy: String) {
    val total: Int get() = tasks * dates * rows
    val filesPerDate: Int get() = when (strategy) {
        "coalesce", "repartition" -> 1
        "capped" -> tasks * ((rows + 4) / 5)
        else -> tasks
    }
    fun path(): String = "/files?tasks=$tasks&dates=$dates&rows=$rows&strategy=$strategy"
}

fun fields(outcome: Outcome): Map<String, String> = outcome.body.lineSequence()
    .filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

fun dates(count: Int): List<String> = (1..count).map { "2026-01-0$it" }

fun refresh(client: HttpClient, base: String): Boolean {
    val modes = listOf("STATIC", "DYNAMIC", "DYNAMIC", "STATIC")
    val outcomes = runLoad(client, modes.mapIndexed { index, mode ->
        Arrival(index * 150L, post(base, "/overwrite?mode=$mode", "", Duration.ofSeconds(180)))
    })
    var partitions = 0
    var records = 0
    for ((index, outcome) in outcomes.withIndex()) {
        val response = fields(outcome)
        val expectedDates = if (modes[index] == "STATIC") "2026-01-02" else dates(3).joinToString(",")
        val expectedIds = if (modes[index] == "STATIC") "20,21" else "1,3,20,21"
        val expectedRows = if (modes[index] == "STATIC") "2" else "4"
        if (response["dates"] != expectedDates) partitions++
        if (response["ids"] != expectedIds || response["rows"] != expectedRows) records++
    }
    val completed = outcomes.count { it.status == 200 && it.error == null }
    metric("requests", outcomes.size)
    metric("completed", completed)
    metric("partition_mismatches", partitions)
    metric("data_mismatches", records)
    metric("static_partitions", fields(outcomes[0])["dates"]?.split(',')?.filter { it.isNotEmpty() }?.size ?: 0)
    metric("dynamic_partitions", fields(outcomes[1])["dates"]?.split(',')?.filter { it.isNotEmpty() }?.size ?: 0)
    return completed == 4 && partitions == 0 && records == 0
}

fun layouts(client: HttpClient, base: String): Boolean {
    val cases = listOf(2 to 2, 4 to 3).flatMap { (tasks, partitions) ->
        listOf("plain", "coalesce", "repartition", "capped").map { strategy ->
            FileCase(tasks, partitions, if (tasks == 2) 6 else 8, strategy)
        }
    }
    val outcomes = runLoad(client, cases.mapIndexed { index, case ->
        Arrival(index * 150L, post(base, case.path(), "", Duration.ofSeconds(180)))
    })
    var fileMismatches = 0
    var dataMismatches = 0
    var stageMismatches = 0
    var limitViolations = 0
    val totals = mutableMapOf<String, Int>()
    for ((index, case) in cases.withIndex()) {
        val response = fields(outcomes[index])
        val expectedFiles = case.dates * case.filesPerDate
        val expectedDirectories = dates(case.dates).joinToString(",") { "dt=$it:${case.filesPerDate}" }
        val actualFiles = response["files"]?.toIntOrNull()
        if (actualFiles != expectedFiles || response["per_date"] != expectedDirectories) fileMismatches++
        val expectedIds = (0 until case.total).joinToString(",")
        val expectedRecords = (0 until case.total).joinToString(",") { "$it@2026-01-0${it % case.dates + 1}" }
        if (response["rows"] != case.total.toString() || response["ids"] != expectedIds ||
            response["records"] != expectedRecords) dataMismatches++
        val expectedTasks = if (case.strategy == "coalesce") 1 else case.tasks
        if (response["first_tasks"]?.toIntOrNull() != expectedTasks) stageMismatches++
        if (case.strategy == "capped" && response["max_file_rows"]?.toIntOrNull() !in 1..5) limitViolations++
        totals[case.strategy] = totals.getOrDefault(case.strategy, 0) + (actualFiles ?: 0)
    }
    val completed = outcomes.count { it.status == 200 && it.error == null }
    metric("requests", outcomes.size)
    metric("completed", completed)
    metric("file_mismatches", fileMismatches)
    metric("data_mismatches", dataMismatches)
    metric("stage_mismatches", stageMismatches)
    metric("limit_violations", limitViolations)
    metric("plain_files", totals.getOrDefault("plain", 0))
    metric("coalesced_files", totals.getOrDefault("coalesce", 0))
    metric("repartitioned_files", totals.getOrDefault("repartition", 0))
    metric("capped_files", totals.getOrDefault("capped", 0))
    return completed == 8 && fileMismatches == 0 && dataMismatches == 0 &&
        stageMismatches == 0 && limitViolations == 0
}

fun experiment(program: File, scenario: String): Boolean {
    requireDocker()
    val compose = File("compose.yaml")
    val stack = ComposeStack(compose, "spark-write", programEnvironment(program))
    return try {
        stack.up(120)
        val port = stack.port("app", 8080)
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
            val base = "http://127.0.0.1:$port"
            check(client.send(get(base, "/health"), java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200)
            val passed = when (scenario) {
                "daily-refresh" -> refresh(client, base)
                "batch-layout" -> layouts(client, base)
                else -> error("unknown scenario")
            }
            if (!passed) {
                val logs = ProcessBuilder("docker", "compose", "-p", stack.project, "-f", compose.absolutePath,
                    "logs", "--no-color", "--tail", "100", "app").redirectErrorStream(true)
                    .apply { environment().putAll(LAB_IMAGES + programEnvironment(program)) }.start()
                System.err.println(logs.inputStream.bufferedReader().readText().takeLast(12000))
                logs.waitFor()
            }
            passed
        }
    } finally {
        stack.close()
    }
}

val result = try {
    require(args.size == 2 && File(args[0]).isAbsolute)
    require(args[1] in setOf("daily-refresh", "batch-layout"))
    if (experiment(File(args[0]), args[1])) 0 else 1
} catch (failure: Exception) {
    System.err.println("experiment error: ${failure.message}")
    2
}
exitProcess(result)
