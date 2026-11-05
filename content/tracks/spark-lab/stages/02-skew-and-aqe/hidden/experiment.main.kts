@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.security.MessageDigest
import java.io.File
import java.net.http.HttpClient
import java.time.Duration
import kotlin.system.exitProcess

data class Report(
    val rows: Int,
    val digest: String,
    val partitions: Int,
    val shufflePartitions: Int,
    val work: List<Double>,
    val times: List<Double>,
    val adaptive: Boolean,
    val skewed: Boolean,
)

fun report(body: String): Report {
    val fields = body.lineSequence().filter { it.isNotBlank() }.associate {
        it.substringBefore('=') to it.substringAfter('=')
    }
    fun numbers(key: String): List<Double> = fields.getValue(key).split(',').map { value ->
        value.toDouble().also { require(it.isFinite() && it >= 0) }
    }
    return Report(
        fields.getValue("rows").toInt(), fields.getValue("digest"),
        fields.getValue("partitions").toInt(), fields.getValue("shuffle_partitions").toInt(),
        numbers("partition_rows"), numbers("task_ms"),
        fields.getValue("adaptive").toBooleanStrict(), fields.getValue("skewed").toBooleanStrict(),
    ).also { require(it.work.size == it.partitions && it.times.size == it.partitions && it.partitions > 0) }
}

fun expected(profile: String): Pair<Int, String> {
    val rows = (0L until 4096L).flatMap { id ->
        val hot = if (profile == "ledger") id < 3072 else id % 4 != 0L
        val key = if (hot) 0L else 1L + id % 127
        val weights = if (key == 0L) listOf(3L, 7L) else listOf(key + 3)
        weights.map { weight -> "$id:$weight:${id % 17 + 1}" }
    }.sorted()
    val digest = MessageDigest.getInstance("SHA-256").digest(rows.joinToString("\n").toByteArray())
        .joinToString("") { "%02x".format(it) }
    return rows.size to digest
}

fun ratio(values: List<Double>, floor: Double): Double =
    values.max() / maxOf(floor, percentile(values, 0.5))

fun composeDefinition(): String = """
services:
  app:
    image: ${'$'}{JVM_RUNNER_IMAGE}
    volumes: ["${'$'}{PROGRAM_DIR}:/work:ro"]
    working_dir: /work
    command: ["lab-run", "spark", "lab.MainKt"]
    environment:
      PORT: "8080"
      JAVA_OPTS: "-Xms256m -Xmx1g"
      SPARK_LOCAL_IP: "127.0.0.1"
    ports: ["127.0.0.1::8080"]
    healthcheck:
      test: ["CMD-SHELL", "curl -fs localhost:8080/health >/dev/null"]
      interval: 1s
      timeout: 2s
      retries: 90
    deploy:
      resources:
        limits: { cpus: "2", memory: 1536m }
""".trimIndent()

fun exercise(stack: ComposeStack): Int {
    stack.up(120)
    val base = "http://127.0.0.1:${stack.port("app", 8080)}"
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build().use { client ->
        val cases = listOf("ledger" to "off", "ledger" to "on", "ledger" to "factor",
            "ledger" to "bytes", "ledger" to "tuned", "replay" to "tuned")
        val outcomes = runLoad(client, cases.mapIndexed { index, (profile, mode) ->
            Arrival(index * 100L, post(base, "/run?profile=$profile&mode=$mode", "", Duration.ofSeconds(180)))
        })
        check(outcomes.all { it.error == null }) { "HTTP transport failure" }
        val successful = outcomes.count { it.status == 200 }
        metric("completed", successful)
        if (successful != cases.size) return 1
        val reports = outcomes.map { report(it.body) }
        val incorrect = reports.zip(cases).count { (actual, case) ->
            val (rows, digest) = expected(case.first)
            actual.rows != rows || actual.digest != digest
        }
        val baseline = reports[0]
        val adaptive = reports[1]
        val blocked = reports.subList(2, 4)
        val tuned = reports.takeLast(2)
        val workRatio = tuned.maxOf { ratio(it.work, 1.0) }
        val taskRatio = tuned.maxOf { ratio(it.times, 250.0) }
        val partitions = tuned.maxOf { it.partitions }
        val shufflePartitions = tuned.maxOf { it.shufflePartitions }
        val withinPartitionLimits = tuned.all { it.partitions <= 32 && it.shufflePartitions <= 32 }
        val controls = !baseline.adaptive && adaptive.adaptive && adaptive.skewed &&
            adaptive.partitions > baseline.partitions && blocked.all {
                it.adaptive && !it.skewed && it.partitions == baseline.partitions
            }
        metric("incorrect_results", incorrect)
        metric("baseline_work_ratio", ratio(baseline.work, 1.0))
        metric("baseline_task_ratio", ratio(baseline.times, 250.0))
        metric("tuned_work_ratio", workRatio)
        metric("tuned_task_ratio", taskRatio)
        metric("tuned_partitions", partitions)
        metric("tuned_shuffle_partitions", shufflePartitions)
        metric("adaptive_partitions", adaptive.partitions)
        metric("control_checks", if (controls) 1 else 0)
        return if (incorrect == 0 && controls && workRatio <= 6.0 && taskRatio <= 12.0 && withinPartitionLimits) 0 else 1
    }
}

fun mainExperiment(): Int {
    require(args.size == 2 && File(args[0]).isAbsolute && args[1] == "daily-join")
    requireDocker()
    val compose = File.createTempFile("cresvoid-skew-", ".yaml")
    try {
        compose.writeText(composeDefinition())
        return ComposeStack(compose, "spark-skew", programEnvironment(File(args[0]))).use { stack ->
            exercise(stack)
        }
    } finally {
        compose.delete()
    }
}

val result = try {
    mainExperiment()
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.message}")
    2
}
exitProcess(result)
