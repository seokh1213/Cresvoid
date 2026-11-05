@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

data class Output(val batch: Long, val minute: Int, val count: Int)
data class Snapshot(val watermark: Long, val dropped: Long, val attempts: List<Long>, val rows: List<Output>)

fun snapshot(text: String): Snapshot {
    val lines = text.lineSequence().filter { it.isNotBlank() }.map { it.split(' ') }.toList()
    fun scalar(name: String): Long = lines.single { it[0] == name }[1].toLong()
    return Snapshot(scalar("WATERMARK"), scalar("DROPPED"),
        lines.filter { it[0] == "ATTEMPT" }.map { it[1].toLong() },
        lines.filter { it[0] == "ROW" }.map { Output(it[1].toLong(), it[2].toInt(), it[3].toInt()) })
}

class Probe(private val base: String) : AutoCloseable {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    var httpErrors = 0
    var outputErrors = 0
    var watermarkErrors = 0
    var droppedErrors = 0
    var replayErrors = 0
    var observation = 0
    var firstOutputObservation = 0
    var firstExpectedRows = 0
    var firstActualRows = 0

    fun ready() {
        val deadline = System.nanoTime() + Duration.ofSeconds(150).toNanos()
        while (System.nanoTime() < deadline) {
            val result = runLoad(client, listOf(Arrival(0, get(base, "/health", Duration.ofSeconds(2))))).single()
            if (result.status == 200) return
            Thread.sleep(100)
        }
        error("서비스 준비 시간 초과")
    }

    fun request(path: String, body: String = "", expectedStatus: Int = 200): String {
        val result = runLoad(client, listOf(Arrival(0, post(base, path, body, Duration.ofSeconds(150))))).single()
        check(result.error == null) { "HTTP 실험 오류: ${result.error}" }
        if (result.status != expectedStatus) httpErrors++
        check(result.status in setOf(200, 503)) { "HTTP ${result.status}: ${result.body}" }
        System.err.println("$path [$body] => ${result.status}\n${result.body}")
        return result.body
    }

    fun verify(state: Snapshot, expected: List<Pair<Int, Int>>, watermark: Long, dropped: Long) {
        observation++
        val actual = state.rows.map { it.minute to it.count }.groupingBy { it }.eachCount()
        if (actual != expected.groupingBy { it }.eachCount()) {
            outputErrors++
            if (firstOutputObservation == 0) {
                firstOutputObservation = observation
                firstExpectedRows = expected.size
                firstActualRows = state.rows.size
            }
        }
        if (state.watermark != watermark) watermarkErrors++
        if (state.dropped != dropped) droppedErrors++
    }

    fun windowSequence() {
        val batches = listOf("1,4,12", "8,16", "31", "3,14,26,34", "51")
        val watermarks = listOf(2L, 6L, 21L, 24L, 41L)
        val append = listOf(emptyList(), emptyList(), listOf(0 to 3, 10 to 2),
            listOf(0 to 3, 10 to 2), listOf(0 to 3, 10 to 2, 20 to 1, 30 to 2))
        val update = listOf(listOf(0 to 2, 10 to 1), listOf(0 to 3, 10 to 2),
            listOf(30 to 1), listOf(20 to 1, 30 to 2), listOf(50 to 1))
        for (mode in listOf("append", "update")) {
            request("/open", mode)
            val cumulative = mutableListOf<Pair<Int, Int>>()
            for (index in batches.indices) {
                val state = snapshot(request("/batch", batches[index]))
                cumulative.addAll(update[index])
                verify(state, if (mode == "append") append[index] else cumulative,
                    watermarks[index], if (index >= 3) 2 else 0)
                if (state.attempts.distinct().size != state.attempts.size) replayErrors++
            }
        }
    }

    fun recoverySequence() {
        request("/open", "update")
        val first = snapshot(request("/fail", "2,7", 503))
        verify(first, listOf(0 to 2), -1, 0)
        val firstFailedBatch = first.attempts.last()
        val retried = snapshot(request("/restart"))
        verify(retried, listOf(0 to 2), -3, 0)
        verifyReplays(retried, setOf(firstFailedBatch))
        if (!sameRows(first, retried)) replayErrors++
        val second = snapshot(request("/batch", "8,14"))
        val expected = listOf(0 to 2, 0 to 3, 10 to 1)
        verify(second, expected, 4, 0)
        val finalExpected = expected + listOf(0 to 4, 10 to 2)
        val failed = snapshot(request("/fail", "9,17", 503))
        verify(failed, finalExpected, 4, 0)
        val failedBatches = setOf(firstFailedBatch, failed.attempts.last())
        if (failedBatches.size != 2) replayErrors++
        val recovered = snapshot(request("/restart"))
        verify(recovered, finalExpected, 7, 0)
        verifyReplays(recovered, failedBatches)
        if (!sameRows(failed, recovered)) replayErrors++
        val stable = snapshot(request("/restart"))
        verify(stable, finalExpected, 7, 0)
        verifyReplays(stable, failedBatches)
        if (!sameRows(stable, recovered)) replayErrors++
    }

    fun verifyReplays(state: Snapshot, failedBatches: Set<Long>) {
        val counts = state.attempts.groupingBy { it }.eachCount()
        if (failedBatches.any { counts[it] != 2 } ||
            counts.any { (batch, count) -> count != if (batch in failedBatches) 2 else 1 } ||
            state.rows.any { it.batch !in counts }) replayErrors++
    }

    fun sameRows(first: Snapshot, second: Snapshot): Boolean =
        first.rows.groupingBy { it }.eachCount() == second.rows.groupingBy { it }.eachCount()

    fun report(): Int {
        metric("http_errors", httpErrors)
        metric("output_errors", outputErrors)
        metric("watermark_errors", watermarkErrors)
        metric("dropped_errors", droppedErrors)
        metric("replay_errors", replayErrors)
        metric("first_output_observation", firstOutputObservation)
        metric("first_expected_rows", firstExpectedRows)
        metric("first_actual_rows", firstActualRows)
        return if (listOf(httpErrors, outputErrors, watermarkErrors, droppedErrors, replayErrors).all { it == 0 }) 0 else 1
    }

    override fun close() = client.close()
}

require(args.size == 2 && File(args[0]).isAbsolute)
require(args[1] in setOf("window-sequence", "recovery-sequence"))
requireDocker()
val result = try {
    ComposeStack(File("solution/compose.yaml"), "spark-watermark", programEnvironment(File(args[0]))).use { stack ->
        stack.up(150)
        Probe("http://127.0.0.1:${stack.port("app", 8080)}").use { probe ->
            probe.ready()
            when (args[1]) {
                "window-sequence" -> probe.windowSequence()
                "recovery-sequence" -> probe.recoverySequence()
            }
            probe.report()
        }
    }
} catch (failure: Exception) {
    System.err.println("실험 오류: ${failure.message}")
    2
}
exitProcess(result)
