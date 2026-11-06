@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

data class Scenario(val seed: Int, val order: String)
data class BatchRecord(
    val phase: String,
    val candidate: String,
    val calls: Int,
    val retention: String,
    val checksum: Long,
    val nanos: Long,
) {
    val perCall: Double get() = nanos.toDouble() / calls
}

fun scenario(name: String): Scenario = when (name) {
    "first-window" -> Scenario(17, "abba")
    "second-window" -> Scenario(29, "baab")
    else -> error("Unknown scenario")
}

fun parse(body: String): List<BatchRecord>? {
    val lines = body.removeSuffix("\n").lines()
    if (lines.size != 10) return null
    return lines.map { line ->
        val fields = line.split('|')
        if (fields.size != 6) return null
        val phase = fields[0]
        val candidate = fields[1]
        val countText = fields[2]
        val retention = fields[3]
        val checksumText = fields[4]
        val timeText = fields[5]
        if (phase !in setOf("cold", "warmup", "discard", "measure") ||
            candidate !in setOf("A", "B") || retention !in setOf("USED", "DISCARDED")) return null
        if (!countText.matches(Regex("[0-9]+")) || !checksumText.matches(Regex("-?[0-9]+")) ||
            !timeText.matches(Regex("[0-9]+"))) return null
        val calls = countText.toIntOrNull() ?: return null
        val checksum = checksumText.toLongOrNull() ?: return null
        val nanos = timeText.toLongOrNull() ?: return null
        if (calls <= 0 || nanos <= 0) return null
        BatchRecord(phase, candidate, calls, retention, checksum, nanos)
    }
}

// Use unsigned values in Long arithmetic to derive the signed Int result independently.
fun checksum(seed: Int, calls: Int): Long {
    var sum = 0L
    for (index in 0 until calls) {
        var word = (seed.toLong() + index) and 0xffff_ffffL
        repeat(64) {
            word = ((word xor (word ushr 13)) * 1_664_525L + 1_013_904_223L) and 0xffff_ffffL
        }
        sum += if (word >= 0x8000_0000L) word - 0x1_0000_0000L else word
    }
    return sum
}

fun procedureErrors(rows: List<BatchRecord>, order: String): Int = rows.withIndex().count { (index, row) ->
    val phase = when (index) {
        0, 1 -> "cold"
        2, 3 -> "warmup"
        4, 5 -> "discard"
        else -> "measure"
    }
    val candidate = if (index < 6) (if (index % 2 == 0) "A" else "B")
        else order[index - 6].uppercase()
    val calls = when (index) {
        0, 1 -> 1_000
        2, 3 -> 60_000
        else -> 500_000
    }
    row.phase != phase || row.candidate != candidate || row.calls != calls
}

fun consumptionErrors(rows: List<BatchRecord>, seed: Int): Int {
    val expected = mutableMapOf<Int, Long>()
    return rows.count { row ->
        val retention = if (row.phase == "discard") "DISCARDED" else "USED"
        val sum = if (retention == "DISCARDED") 0L
            else expected.getOrPut(row.calls) { checksum(seed, row.calls) }
        row.retention != retention || row.checksum != sum
    }
}

fun ratios(rows: List<BatchRecord>): List<Double> {
    val measured = rows.drop(6)
    val a = measured.filter { it.candidate == "A" }
    val b = measured.filter { it.candidate == "B" }
    val usedMean = measured.map { it.perCall }.average()
    return listOf(
        rows.take(2).map { it.perCall }.average() / usedMean,
        rows.subList(4, 6).map { it.perCall }.average() / usedMean,
        a.map { it.perCall }.average() / b.map { it.perCall }.average(),
        a[0].nanos.toDouble() / a[1].nanos,
        b[0].nanos.toDouble() / b[1].nanos,
    )
}

fun canCompare(rows: List<BatchRecord>): Boolean {
    val measured = rows.drop(6)
    return measured.all { it.phase == "measure" } &&
        measured.count { it.candidate == "A" } == 2 && measured.count { it.candidate == "B" } == 2
}

fun report(outcome: Outcome, scenario: Scenario): Int {
    check(outcome.error == null) { "Observation request failed: ${outcome.error}" }
    val rows = if (outcome.status == 200) parse(outcome.body) else null
    val responseErrors = if (rows == null) 1 else 0
    val procedureErrors = rows?.let { procedureErrors(it, scenario.order) } ?: 0
    val consumptionErrors = rows?.let { consumptionErrors(it, scenario.seed) } ?: 0
    val observations = if (rows != null && canCompare(rows)) ratios(rows) else List(5) { 0.0 }
    metric("response_errors", responseErrors)
    metric("procedure_errors", procedureErrors)
    metric("consumption_errors", consumptionErrors)
    listOf("cold_warm_ratio", "discard_used_ratio", "a_b_ratio", "a_position_ratio", "b_position_ratio")
        .zip(observations).forEach { (name, value) ->
            check(value.isFinite()) { "Non-finite ratio" }
            metric(name, value)
        }
    return if (responseErrors + procedureErrors + consumptionErrors == 0) 0 else 1
}

fun experiment(programDir: File, scenario: Scenario): Int {
    require(programDir.isAbsolute && File(programDir, "run.sh").canExecute())
    require(Runtime.version().feature() == 21) { "JDK 21 required" }
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    val environment = mapOf(
        "PORT" to port.toString(),
        "JAVA_TOOL_OPTIONS" to "-Xms64m -Xmx256m -XX:ActiveProcessorCount=2 -XX:+TieredCompilation",
        "JDK_JAVA_OPTIONS" to "",
        "_JAVA_OPTIONS" to "",
        "PATH" to File(System.getProperty("java.home"), "bin").absolutePath + File.pathSeparator + System.getenv("PATH"),
    )
    val program = Program(programDir, environment, base)
    try {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            val request = get(base, "/observe?seed=${scenario.seed}&order=${scenario.order}", Duration.ofSeconds(30))
            val outcomes = runLoad(client, listOf(Arrival(100, request)))
            return report(outcomes.single(), scenario)
        }
    } finally {
        program.close()
    }
}

val result = try {
    require(args.size == 2) { "Program absolute path and scenario required" }
    experiment(File(args[0]), scenario(args[1]))
} catch (failure: Exception) {
    System.err.println("Experiment error: ${failure.javaClass.simpleName}: ${failure.message}")
    2
}
exitProcess(result)
