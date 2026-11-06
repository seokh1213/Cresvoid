@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

fun snapshot(client: HttpClient, base: String): Map<String, Long> {
    val response = client.send(get(base, "/stats", Duration.ofSeconds(8)), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "통계를 읽지 못했다" }
    val values = response.body().lineSequence().filter { it.isNotBlank() }.associate { line ->
        val fields = line.split('=', limit = 2)
        check(fields.size == 2)
        fields[0] to fields[1].toLong()
    }
    val required = setOf("successes", "failures", "created", "idle_drops", "age_drops", "idle", "active", "total")
    check(values.keys == required && values.values.all { it >= 0 }) { "통계 계약이 다르다" }
    return values
}

fun runExperiment(program: File): Boolean {
    var passed = false
    ComposeStack(File("compose.yaml"), "spring-06", programEnvironment(program)).use { stack ->
        stack.up(60)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
            val health = client.send(get(base, "/health", Duration.ofSeconds(8)), HttpResponse.BodyHandlers.ofString())
            check(health.statusCode() == 200 && health.body() == "ready")
            val before = snapshot(client, base)
            val initial = runLoad(client, steady(10, 2.0) { get(base, "/query", Duration.ofSeconds(8)) })
            // No request touches JDBC during the pause; stats only reads counters and the MXBean.
            val resumed = runLoad(client, steady(10, 2.0, 90_000) { get(base, "/query", Duration.ofSeconds(8)) } +
                Arrival(89_000, get(base, "/stats", Duration.ofSeconds(8))))
            val pause = resumed.single { it.arrival.request.uri().path == "/stats" }
            check(pause.status == 200) { "휴식 중 통계를 읽지 못했다" }
            val pauseIdle = pause.body.lineSequence().single { it.startsWith("idle=") }.substringAfter('=').toLong()
            val queries = resumed.filter { it.arrival.request.uri().path == "/query" }
            val after = snapshot(client, base)
            val initialOk = initial.count { it.status == 200 && it.body == "42" }
            val resumedOk = queries.count { it.status == 200 && it.body == "42" }
            val businessErrors = after.getValue("failures") - before.getValue("failures")
            val idleDrops = after.getValue("idle_drops") - before.getValue("idle_drops")
            val ageDrops = after.getValue("age_drops") - before.getValue("age_drops")
            val created = after.getValue("created")
            val p95 = percentile((initial + queries).map { it.elapsedMillis }, 0.95)
            val completed = after.getValue("successes") - before.getValue("successes")
            metric("initial_ok", initialOk)
            metric("resumed_ok", resumedOk)
            metric("business_errors", businessErrors)
            metric("idle_drops", idleDrops)
            metric("age_drops", ageDrops)
            metric("connections_created", created)
            metric("pause_idle", pauseIdle)
            metric("query_p95_ms", p95)
            metric("completed_queries", completed)
            passed = initialOk == 20 && resumedOk == 20 && businessErrors == 0L &&
                idleDrops == 0L && ageDrops == 0L && created in 4L..16L && pauseIdle == 4L &&
                after.getValue("total") == 4L && after.getValue("active") == 0L && completed == 40L && p95 <= 5_000
        }
    }
    return passed
}

val result = try {
    require(args.size == 2 && args[1] == "evening-traffic")
    val program = File(args[0])
    require(program.isAbsolute && File(program, "run.sh").canExecute())
    requireDocker()
    if (runExperiment(program)) 0 else 1
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(result)
