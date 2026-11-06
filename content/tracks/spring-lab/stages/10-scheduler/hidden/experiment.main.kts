@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class Observation(val values: Map<String, Long>) {
    fun value(name: String): Long = values[name] ?: -1L
    fun completed(): Boolean = value("long_finished") == 1L && value("pulses") >= 1L
    fun valid(): Boolean = value("long_started") == 1L && completed() && value("expired") == 0L
    fun sameThread(): Boolean = value("long_thread") > 0 && value("long_thread") == value("pulse_thread")
    fun distinctThreads(): Boolean = value("long_thread") > 0 && value("pulse_thread") > 0 && !sameThread()
}

class Measurement(private val base: String, private val client: HttpClient) {
    var httpErrors = 0
        private set
    var missedStarts = 0
        private set

    private fun call(request: HttpRequest): String {
        val outcome = runLoad(client, listOf(Arrival(0, request))).single()
        if (outcome.status != 200) httpErrors++
        return outcome.body
    }

    private fun snapshot(): Observation {
        val values = call(get(base, "/snapshot", Duration.ofSeconds(3))).lineSequence().mapNotNull { line ->
            val key = line.substringBefore('=')
            line.substringAfter('=', "").toLongOrNull()?.let { key to it }
        }.toMap()
        return Observation(values)
    }

    private fun awaitSnapshot(predicate: (Observation) -> Boolean): Observation {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        var observation = snapshot()
        while (!predicate(observation) && System.nanoTime() < deadline) {
            Thread.sleep(50)
            observation = snapshot()
        }
        return observation
    }

    fun observe(mode: String, windowMillis: Long): Observation {
        call(post(base, "/start?mode=$mode", "", Duration.ofSeconds(3)))
        val started = awaitSnapshot { it.value("long_started") == 1L }
        if (started.value("long_started") != 1L) missedStarts++
        val arrivals = (0L until windowMillis step 200).map { at ->
            Arrival(at, get(base, "/snapshot", Duration.ofSeconds(3)))
        } + Arrival(windowMillis, post(base, "/release", "", Duration.ofSeconds(3)))
        httpErrors += runLoad(client, arrivals).count { it.status != 200 }
        return awaitSnapshot { it.completed() }
    }
}

fun awaitReady(client: HttpClient, base: String) {
    val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
    while (System.nanoTime() < deadline) {
        val response = runCatching {
            client.send(get(base, "/health", Duration.ofMillis(500)), HttpResponse.BodyHandlers.ofString())
        }.getOrNull()
        if (response?.statusCode() == 200 && response.body() == "ready") return
        Thread.sleep(100)
    }
    error("서비스 준비 시간 초과")
}

fun measure(program: File, windowMillis: Long): Int {
    val port = freePort()
    val environment = programEnvironment(program) + ("APP_PORT" to port.toString())
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
    val stack = ComposeStack(File("compose.yaml"), "spring-scheduler", environment)
    try {
        stack.up(45)
        val base = "http://127.0.0.1:$port"
        awaitReady(client, base)
        val measurement = Measurement(base, client)
        val baseline = measurement.observe("baseline", windowMillis)
        val comparison = measurement.observe("comparison", windowMillis)
        val contractFailures = listOf(baseline, comparison).count { !it.valid() } + measurement.missedStarts
        metric("baseline_during", baseline.value("during"))
        metric("comparison_during", comparison.value("during"))
        metric("baseline_same_thread", if (baseline.sameThread()) 1 else 0)
        metric("comparison_distinct_thread", if (comparison.distinctThreads()) 1 else 0)
        metric("baseline_first_ms", baseline.value("first_pulse_ms"))
        metric("comparison_first_ms", comparison.value("first_pulse_ms"))
        metric("completed", baseline.value("long_finished") + comparison.value("long_finished"))
        metric("http_errors", measurement.httpErrors)
        metric("contract_failures", contractFailures)
        val passed = baseline.value("during") == 0L && baseline.sameThread() &&
            comparison.value("during") >= 1L && comparison.distinctThreads() &&
            contractFailures == 0 && measurement.httpErrors == 0
        return if (passed) 0 else 1
    } finally {
        stack.close()
        client.close()
    }
}

val result = try {
    require(args.size == 2) { "프로그램 경로와 시나리오 이름이 필요하다" }
    val program = File(args[0])
    require(program.isAbsolute && program.isDirectory) { "프로그램 절대 경로가 필요하다" }
    val windowMillis = when (args[1]) {
        "scheduled-window" -> 1600L
        "extended-window" -> 2600L
        else -> error("알 수 없는 시나리오")
    }
    requireDocker()
    measure(program, windowMillis)
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(result)
