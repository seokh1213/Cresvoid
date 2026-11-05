@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.system.exitProcess

data class RequestCase(val id: Int, val lane: Int, val user: String?, val fail: Boolean, val bytes: Int = 4096)
data class WorkerSnapshot(val lane: Int, val worker: Long, val bytes: Long)

class Counters(val initial: Map<Int, Long>) {
    var completed = 0
    var unexpected = 0
    var leaked = 0
    var workerChanges = if (initial.size == 4 && initial.values.toSet().size == 4) 0 else 1
    var snapshotErrors = 0

    fun collect(cases: List<RequestCase>, outcomes: List<Outcome>) {
        check(cases.size == outcomes.size)
        cases.zip(outcomes).forEach { (case, outcome) ->
            if (outcome.status != 0) completed++
            val fields = outcome.body.trim().split('|')
            val expectedUser = case.user ?: "anonymous"
            val validBody = fields.size == 4 && fields[0] == case.id.toString() &&
                fields[1] == case.lane.toString() && fields[3] == expectedUser &&
                fields[2].toLongOrNull() != null
            if (outcome.status != (if (case.fail) 503 else 200) || !validBody) unexpected++
            if (fields.size == 4 && fields[3] != expectedUser && fields[3] != "anonymous") leaked++
            if (fields.size == 4 && fields[2].toLongOrNull() != initial[case.lane]) workerChanges++
        }
    }
}

fun request(base: String, case: RequestCase): HttpRequest {
    val user = case.user?.let { "&user=$it" }.orEmpty()
    return get(base, "/request?id=${case.id}&lane=${case.lane}&fail=${if (case.fail) 1 else 0}&bytes=${case.bytes}$user",
        Duration.ofSeconds(20))
}

fun snapshot(client: HttpClient, base: String): List<WorkerSnapshot>? {
    val response = runCatching {
        client.send(get(base, "/snapshot", Duration.ofSeconds(20)), HttpResponse.BodyHandlers.ofString())
    }.getOrNull() ?: return null
    if (response.statusCode() != 200) return null
    val rows = response.body().trim().lines().map { line ->
        val fields = line.split('|')
        if (fields.size != 3) return null
        val lane = fields[0].toIntOrNull() ?: return null
        val worker = fields[1].toLongOrNull() ?: return null
        val bytes = fields[2].toLongOrNull() ?: return null
        if (lane !in 0..3 || worker <= 0 || bytes !in 0..1_048_576) return null
        WorkerSnapshot(lane, worker, bytes)
    }
    return rows.takeIf { it.size == 4 && it.map { row -> row.lane }.toSet().size == 4 }
}

fun observePrelude(client: HttpClient, base: String, counters: Counters) {
    repeat(4) { phase ->
        val cases = List(4) { lane ->
            val id = phase * 4 + lane
            RequestCase(id, lane, if (phase % 2 == 0) "user-$id" else null, phase == 2)
        }
        counters.collect(cases, runLoad(client, cases.map { Arrival(0, request(base, it)) }))
    }
}

fun observeTail(client: HttpClient, base: String, counters: Counters) {
    val cases = List(4) { lane -> RequestCase(99_996 + lane, lane, "tail-$lane", true, 1_048_576) }
    counters.collect(cases, runLoad(client, cases.map { Arrival(0, request(base, it)) }))
}

fun runScheduledLoad(client: HttpClient, arrivals: List<Arrival>): List<Outcome> {
    val start = System.nanoTime()
    Executors.newFixedThreadPool(128).use { executor ->
        // Queue arrivals when due; bound simultaneous sends to limit local connection pressure.
        val futures = arrivals.map { arrival ->
            val wait = arrival.atMillis - (System.nanoTime() - start) / 1_000_000
            if (wait > 0) Thread.sleep(wait)
            executor.submit<Outcome> {
                val began = System.nanoTime()
                try {
                    val response = client.send(arrival.request, HttpResponse.BodyHandlers.ofString())
                    Outcome(arrival, response.statusCode(), (System.nanoTime() - began) / 1e6, response.body(), null)
                } catch (error: Exception) {
                    Outcome(arrival, 0, (System.nanoTime() - began) / 1e6, "", error.javaClass.simpleName)
                }
            }
        }
        return futures.map { it.get() }
    }
}

fun measure(client: HttpClient, base: String): Int {
    val before = snapshot(client, base)
    val counters = Counters(before.orEmpty().associate { it.lane to it.worker })
    if (before == null) counters.snapshotErrors++
    observePrelude(client, base, counters)
    val cases = List(99_980) { index ->
        val phase = (index / 4) % 4
        val id = index + 16
        RequestCase(id, index % 4, if (phase % 2 == 0) "user-$id" else null, phase == 2)
    }
    val arrivals = cases.mapIndexed { index, case -> Arrival(index * 1000L / 2500, request(base, case)) }
    val began = System.nanoTime()
    val outcomes = runScheduledLoad(client, arrivals)
    val elapsed = (System.nanoTime() - began) / 1e6
    val errors = outcomes.mapNotNull { it.error }.groupingBy { it }.eachCount()
    if (errors.isNotEmpty()) System.err.println("전송 오류 집계: $errors")
    counters.collect(cases, outcomes)
    observeTail(client, base, counters)
    val after = snapshot(client, base)
    if (after == null) counters.snapshotErrors++
    after.orEmpty().forEach { if (counters.initial[it.lane] != it.worker) counters.workerChanges++ }
    val retained = after.orEmpty().sumOf { it.bytes }
    val p99 = percentile(outcomes.map { it.elapsedMillis }, 0.99)
    metric("completed_requests", counters.completed)
    metric("unexpected_responses", counters.unexpected)
    metric("leaked_responses", counters.leaked)
    metric("worker_changes", counters.workerChanges)
    metric("snapshot_errors", counters.snapshotErrors)
    metric("retained_payload_bytes", retained)
    metric("p99_ms", p99)
    metric("load_elapsed_ms", elapsed)
    return if (counters.completed == 100_000 && counters.unexpected == 0 && counters.leaked == 0 &&
        counters.workerChanges == 0 && counters.snapshotErrors == 0 && retained <= 65_536 && p99 <= 10_000) 0 else 1
}

fun experiment(): Int {
    require(args.size == 2) { "프로그램 절대 경로와 시나리오가 필요하다" }
    val programDir = File(args[0])
    require(programDir.isAbsolute && File(programDir, "run.sh").canExecute())
    require(args[1] == "request-window") { "알 수 없는 시나리오다" }
    val port = freePort()
    val base = "http://127.0.0.1:$port"
    val javaHome = System.getProperty("java.home")
    val environment = mapOf("PORT" to port.toString(), "JAVA_HOME" to javaHome,
        "PATH" to "$javaHome/bin:${System.getenv("PATH")}")
    val program = Program(programDir, environment, base)
    try {
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build().use { client ->
                return measure(client, base)
            }
    } finally {
        program.close()
    }
}

val exitCode = try {
    experiment()
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(exitCode)
