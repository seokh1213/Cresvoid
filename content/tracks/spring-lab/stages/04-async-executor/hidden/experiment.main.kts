@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

class Sample(val lane: String, val responses: List<Outcome>, val before: Map<String, Int>)
class Expected(val submitted: Int, val accepted: Int, val parallel: Int, val queued: Int, val failureBit: Int)
val expected = mapOf(
    "wide" to Expected(100, 100, 4, 96, 1),
    "direct" to Expected(100, 8, 8, 0, 1),
    "buffered" to Expected(100, 16, 8, 8, 1),
    "tuned-small" to Expected(4, 4, 2, 2, 2),
    "tuned-mid" to Expected(9, 9, 4, 5, 4),
    "tuned" to Expected(40, 11, 6, 5, 8),
)
var violations = 0
var failureMask = 0
fun invariant(valid: Boolean, bit: Int) {
    if (!valid) {
        violations++
        failureMask = failureMask or bit
    }
}

fun fetch(client: HttpClient, base: String, path: String): String {
    val response = client.send(get(base, path), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "상태 조회 실패: ${response.statusCode()}" }
    return response.body()
}

fun stats(client: HttpClient, base: String, lane: String): Map<String, Int> =
    fetch(client, base, "/stats?lane=$lane").lineSequence().associate {
        val fields = it.split('=', limit = 2)
        check(fields.size == 2) { "상태 형식 오류" }
        fields[0] to fields[1].toInt()
    }.also { values ->
        check(values.keys == setOf("accepted", "running", "completed", "peak", "queued", "threads", "failed"))
    }

fun settle(client: HttpClient, base: String, lane: String): Map<String, Int> {
    val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
    var current = stats(client, base, lane)
    while (current.getValue("running") != current.getValue("threads") && System.nanoTime() < deadline) {
        Thread.sleep(25)
        current = stats(client, base, lane)
    }
    return current
}

fun burst(client: HttpClient, base: String, lane: String): Sample {
    val count = expected.getValue(lane).submitted
    val arrivals = List(count) { id -> Arrival(id.toLong(), post(base, "/jobs?lane=$lane&id=$id", "")) }
    val responses = runLoad(client, arrivals)
    return Sample(lane, responses, settle(client, base, lane))
}

fun checkSnapshot(sample: Sample) {
    val target = expected.getValue(sample.lane)
    val before = sample.before
    invariant(sample.responses.size == target.submitted && sample.responses.all { it.status == 202 || it.status == 503 }, target.failureBit)
    invariant(sample.responses.count { it.status == 202 } == target.accepted, target.failureBit)
    invariant(sample.responses.count { it.status == 503 } == target.submitted - target.accepted, target.failureBit)
    invariant(before.getValue("accepted") == target.accepted, target.failureBit)
    invariant(before.getValue("running") == target.parallel && before.getValue("peak") == target.parallel, target.failureBit)
    invariant(before.getValue("threads") == target.parallel, target.failureBit)
    invariant(before.getValue("queued") == target.queued, target.failureBit)
    invariant(before.getValue("completed") == 0 && before.getValue("failed") == 0, target.failureBit)
    invariant(percentile(sample.responses.map { it.elapsedMillis }, 0.95) <= 3000, 16)
}

fun awaitDrain(client: HttpClient, base: String, samples: List<Sample>): Boolean {
    val deadline = System.nanoTime() + Duration.ofSeconds(75).toNanos()
    do {
        val drained = samples.all { sample ->
            val current = stats(client, base, sample.lane)
            current.getValue("running") == 0 && current.getValue("queued") == 0 &&
                current.getValue("completed") == sample.responses.count { it.status == 202 }
        }
        if (drained) return true
        Thread.sleep(100)
    } while (System.nanoTime() < deadline)
    return false
}

fun checkEvents(client: HttpClient, base: String, sample: Sample): Long {
    val rows = fetch(client, base, "/events?lane=${sample.lane}").lineSequence()
        .filter { it.isNotBlank() }.map { it.split(',') }.toList()
    check(rows.all { it.size == 5 }) { "이벤트 형식 오류" }
    val accepted = sample.responses.filter { it.status == 202 }
        .map { it.arrival.request.uri().rawQuery.substringAfter("id=").toInt() }.toSet()
    invariant(rows.map { it[0].toInt() }.toSet() == accepted && rows.size == accepted.size, 64)
    invariant(rows.all {
        val submitted = it[1].toLong()
        val started = it[2].toLong()
        val finished = it[3].toLong()
        submitted >= 0 && started >= submitted && finished - started >= 1000 &&
            it[4].startsWith("async-${sample.lane}-")
    }, 64)
    val after = stats(client, base, sample.lane)
    invariant(after.getValue("accepted") == accepted.size && after.getValue("completed") == accepted.size &&
        after.getValue("running") == 0 && after.getValue("queued") == 0 && after.getValue("failed") == 0, 32)
    invariant(after.getValue("peak") == expected.getValue(sample.lane).parallel, 64)
    return rows.maxOfOrNull { it[3].toLong() } ?: 0L
}

fun checkRecovery(client: HttpClient, base: String) {
    val before = stats(client, base, "tuned").getValue("completed")
    val responses = runLoad(client, steady(100, 0.04) { id -> post(base, "/jobs?lane=tuned&id=${100 + id}", "") })
    invariant(responses.size == 4 && responses.all { it.status == 202 }, 128)
    val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
    var after = stats(client, base, "tuned")
    while ((after.getValue("completed") < before + 4 || after.getValue("running") != 0 ||
            after.getValue("queued") != 0) && System.nanoTime() < deadline) {
        Thread.sleep(50)
        after = stats(client, base, "tuned")
    }
    invariant(after.getValue("completed") == before + 4 && after.getValue("running") == 0, 128)
    invariant(after.getValue("queued") == 0 && after.getValue("peak") <= 6, 128)
    invariant(after.getValue("accepted") == before + 4 && after.getValue("failed") == 0, 128)
    val rows = fetch(client, base, "/events?lane=tuned").lineSequence()
        .filter { it.isNotBlank() }.map { it.split(',') }.filter { it[0].toInt() >= 100 }.toList()
    check(rows.all { it.size == 5 }) { "이벤트 형식 오류" }
    invariant(rows.size == 4 && rows.map { it[0].toInt() }.toSet() == (100..103).toSet(), 128)
    invariant(rows.all {
        val submitted = it[1].toLong()
        val started = it[2].toLong()
        submitted >= 0 && started >= submitted && it[3].toLong() - started >= 1000 &&
            it[4].startsWith("async-tuned-")
    }, 128)
}

fun experiment(): Int {
    require(args.size == 2 && File(args[0]).isAbsolute && args[1] == "submission-window")
    requireDocker()
    val stack = ComposeStack(File("compose.yaml"), "spring-04-async", programEnvironment(File(args[0])))
    try {
        stack.up(120)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            check(fetch(client, base, "/health") == "ready")
            val samples = expected.keys.map { burst(client, base, it) }
            samples.forEach(::checkSnapshot)
            samples.forEach { sample ->
                val result = client.send(post(base, "/release?lane=${sample.lane}", ""), HttpResponse.BodyHandlers.ofString())
                invariant(result.statusCode() == 200, 32)
            }
            invariant(awaitDrain(client, base, samples), 32)
            samples.forEach { checkEvents(client, base, it) }
            checkRecovery(client, base)
            val tuned = samples.last()
            samples.take(3).forEach { metric("${it.lane}_parallel", it.before.getValue("peak")) }
            metric("tuned_parallel", tuned.before.getValue("peak"))
            metric("tuned_queue", tuned.before.getValue("queued"))
            metric("tuned_accepted", tuned.responses.count { it.status == 202 })
            metric("tuned_rejected", tuned.responses.count { it.status == 503 })
            metric("max_lane_submit_p95_ms", samples.maxOf { percentile(it.responses.map { response -> response.elapsedMillis }, 0.95) })
            metric("failure_mask", failureMask)
            metric("invariant_failures", violations)
        }
    } finally {
        stack.close()
    }
    return if (violations == 0) 0 else 1
}

val result = try { experiment() } catch (error: Exception) {
    System.err.println("실험 오류: ${error.javaClass.simpleName}: ${error.message}")
    EXPERIMENT_ERROR
}
exitProcess(result)
