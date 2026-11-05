@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

data class Setting(val size: Int, val cut: Int)

fun values(body: String): Map<String, String> {
    val lines = body.trim().lines()
    require(lines.all { '=' in it }) { "key=value 응답이 필요하다" }
    val pairs = lines.map { it.substringBefore('=') to it.substringAfter('=') }
    require(pairs.map { it.first }.distinct().size == pairs.size) { "duplicate response key" }
    return pairs.toMap()
}

fun ids(value: String): List<Int> = if (value.isEmpty()) emptyList() else value.split(',').map(String::toInt)

fun evaluate(response: Map<String, String>, setting: Setting): Boolean {
    val raw = ids(response.getValue("raw"))
    val published = ids(response.getValue("published"))
    val expected = (0 until setting.size).toSet()
    val publicationErrors = (expected - published.toSet()).size +
        (published.toSet() - expected).size + published.size - published.distinct().size
    val expectedNumbers = mapOf(
        "counted" to setting.size.toLong(),
        "transformation_updates" to 2L * setting.size,
        "action_updates" to setting.size.toLong(),
        "failed_action_updates" to 0L,
        "recovered_updates" to setting.size.toLong(),
        "driver_value" to 0L,
        "failed_jobs" to 1L,
        "submissions" to 2L,
        "slow_partitions" to 1L,
        "speculative_tasks" to 0L,
    )
    val numbers = expectedNumbers.keys.associateWith { response.getValue(it).toLong() }
    val observations = expectedNumbers.map { (key, value) -> numbers.getValue(key) == value } + listOf(
        response.getValue("speculation_enabled") == "true",
        ids(response.getValue("task_attempts")) == listOf(0, 0),
        raw == (0 until setting.cut).toList() + (0 until setting.size).toList(),
    )
    val observationErrors = observations.count { !it }
    for (key in listOf("transformation_updates", "action_updates", "failed_action_updates", "recovered_updates", "driver_value")) {
        metric(key, numbers.getValue(key))
    }
    metric("raw_rows", raw.size)
    metric("published_rows", published.size)
    metric("publication_errors", publicationErrors)
    metric("speculative_tasks", numbers.getValue("speculative_tasks"))
    metric("observation_errors", observationErrors)
    return publicationErrors == 0 && published.size == setting.size && observationErrors == 0
}

fun experiment(): Boolean {
    require(args.size == 2) { "usage: experiment.main.kts <absolute program directory> <scenario>" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile)
    val setting = when (args[1]) {
        "sample-round" -> Setting(12, 4)
        "expanded-round" -> Setting(20, 7)
        else -> error("unknown scenario")
    }
    requireDocker()
    return ComposeStack(File("compose.yaml"), "spark-effects", programEnvironment(program)).use { stack ->
        stack.up(100)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
            val health = client.send(get(base, "/health"), java.net.http.HttpResponse.BodyHandlers.ofString())
            check(health.statusCode() == 200 && health.body().trim() == "ready")
            val request = post(base, "/run?size=${setting.size}&cut=${setting.cut}", "", Duration.ofSeconds(90))
            val result = runLoad(client, listOf(Arrival(0, request))).single()
            check(result.error == null && result.status == 200) { "HTTP experiment error: ${result.error ?: result.status}" }
            evaluate(values(result.body), setting)
        }
    }
}

val code = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(code)
