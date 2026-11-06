@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

fun sql(stack: ComposeStack, statement: String): String {
    val output = stack.exec("mysql", "env", "MYSQL_PWD=lab", "mysql", "-uroot", "-N", "-B", "lab", "-e", statement).trim()
    check(!output.contains("ERROR")) { "Fixture query failed: $output" }
    return output
}

fun initialize(stack: ComposeStack) {
    val seeds = (0..6).flatMap { batch -> listOf("($batch,10)", "($batch,30)") }.joinToString(",")
    val weights = (1..16).joinToString(",") { "($it,0)" }
    check(sql(stack, """
        CREATE TABLE slots (batch INT NOT NULL, id INT NOT NULL, PRIMARY KEY(batch,id)) ENGINE=InnoDB;
        CREATE TABLE ballast (id INT PRIMARY KEY, value INT NOT NULL) ENGINE=InnoDB;
        INSERT INTO slots VALUES $seeds;
        INSERT INTO ballast VALUES $weights;
        SELECT 'fixture_ready';
    """.trimIndent()) == "fixture_ready")
}

fun fields(outcome: Outcome): Map<String, String> {
    val lines = outcome.body.removeSuffix("\n").split('\n').map { it.removeSuffix("\r") }
    if (lines.size != 4) return emptyMap()
    val entries = lines.map { it.split('=', limit = 2) }
    if (entries.any { it.size != 2 }) return emptyMap()
    val response = entries.associate { it[0] to it[1] }
    if (response.keys != setOf("a", "b", "a_wait", "reads") ||
        response["a_wait"] !in setOf("0", "1") || response["reads"] != "2"
    ) return emptyMap()
    return response
}

fun editScopeValid(program: File): Boolean {
    fun files(root: File): Map<String, File> = root.walkTopDown()
        .onEnter { it == root || it.name !in setOf("build", ".gradle", ".kotlin", ".idea") }
        .filter { it.isFile && it.name != ".DS_Store" }
        .associateBy { it.relativeTo(root).invariantSeparatorsPath }
    val starter = files(File("../starter"))
    val candidate = files(program)
    return (starter.keys + candidate.keys).all { path ->
        path == "src/main/kotlin/lab/Policy.kt" ||
            (starter[path] != null && candidate[path] != null &&
                starter.getValue(path).readBytes().contentEquals(candidate.getValue(path).readBytes()))
    }
}

fun storedRows(stack: ComposeStack, batch: Int): List<Int> =
    sql(stack, "SELECT id FROM slots WHERE batch=$batch ORDER BY id").lines().filter { it.isNotBlank() }.map { it.toInt() }

fun experiment(program: File, scenario: String): Boolean {
    val duplicateCase = scenario == "repeated-arrivals"
    require(duplicateCase || scenario == "adjacent-arrivals")
    val environment = programEnvironment(program)
    return ComposeStack(File("compose.yaml"), "db-gap-lock", environment).use { stack ->
        stack.up(150)
        initialize(stack)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
        val observation = runLoad(client, listOf(Arrival(0, post(base, "/observe", "", Duration.ofSeconds(25))))).single()
        val trace = fields(observation)
        val observedResults = listOf(trace["a"], trace["b"])
        val observedDeadlocks = observedResults.count { it == "deadlock" }
        val observationRows = storedRows(stack, 0)
        val survivor = if (trace["a"] == "inserted") 20 else 21
        val ballast = sql(stack, "SELECT SUM(value) FROM ballast").toInt()
        val observationValid = observation.status == 200 && observation.error == null && trace["reads"] == "2" && trace["a_wait"] == "1" &&
            observedDeadlocks == 1 && observedResults.count { it == "inserted" } == 1 &&
            observationRows == listOf(10, survivor, 30) && ballast == (if (survivor == 20) 16 else 0)
        val arrivals = steady(2, 3.0) { index ->
            val left = 12 + index
            val right = if (duplicateCase) left else left + 7
            post(base, "/pair?batch=${index + 1}&left=$left&right=$right", "", Duration.ofSeconds(25))
        }
        val outcomes = runLoad(client, arrivals)
        val results = outcomes.flatMap { outcome ->
            val response = fields(outcome)
            if (outcome.status == 200 && outcome.error == null && response.isNotEmpty()) {
                listOf(response.getValue("a"), response.getValue("b"))
            } else emptyList()
        }
        val httpErrors = outcomes.count { it.status != 200 || it.error != null || fields(it).isEmpty() }
        val inserted = results.count { it == "inserted" }
        val duplicates = results.count { it == "duplicate" }
        val deadlocks = results.count { it == "deadlock" }
        val badResponses = results.count { it !in setOf("inserted", "duplicate", "deadlock") }
        val rowMismatches = outcomes.indices.count { index ->
            val left = 12 + index
            val expected = (listOf(10, 30, left) + if (duplicateCase) emptyList() else listOf(left + 7)).sorted()
            storedRows(stack, index + 1) != expected
        } + if (sql(stack, "SELECT COUNT(*) FROM slots WHERE batch NOT BETWEEN 0 AND 6").toInt() != 0) 1 else 0
        val expectedInserted = if (duplicateCase) 6 else 12
        val expectedDuplicates = if (duplicateCase) 6 else 0
        val pairMismatches = outcomes.count { outcome ->
            val response = fields(outcome)
            val pair = listOf(response["a"], response["b"])
            pair.count { it == "inserted" } != (if (duplicateCase) 1 else 2) ||
                pair.count { it == "duplicate" } != (if (duplicateCase) 1 else 0)
        }
        val observationPreserved = storedRows(stack, 0) == observationRows &&
            sql(stack, "SELECT SUM(value) FROM ballast").toInt() == ballast
        metric("observe_valid", if (observationValid && observationPreserved) 1 else 0)
        metric("observe_wait", trace["a_wait"]?.toIntOrNull() ?: 0)
        metric("observe_deadlocks", observedDeadlocks)
        metric("requests", outcomes.size)
        metric("http_errors", httpErrors + badResponses)
        metric("inserted", inserted)
        metric("duplicates", duplicates)
        metric("deadlocks", deadlocks)
        metric("row_mismatches", rowMismatches)
        metric("pair_mismatches", pairMismatches)
        editScopeValid(program) && observationValid && observationPreserved &&
            outcomes.size == 6 && httpErrors == 0 && badResponses == 0 && pairMismatches == 0 &&
            inserted == expectedInserted && duplicates == expectedDuplicates && deadlocks == 0 && rowMismatches == 0
    }
}

requireDocker()
val result = try {
    require(args.size == 2)
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile)
    if (experiment(program, args[1])) 0 else 1
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.message}")
    EXPERIMENT_ERROR
}
exitProcess(result)
