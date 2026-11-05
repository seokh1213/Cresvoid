// Shared Docker helpers for lab experiments.
// Import from a stage's hidden/experiment.main.kts with:
//   @file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Every service image a lab may start, pinned by digest so all learners run the same bits.
 * Compose files refer to these through variables such as ${REDIS_IMAGE}.
 */
val LAB_IMAGES: Map<String, String> = mapOf(
    // redis 8.10.3, alpine
    "REDIS_IMAGE" to "redis@sha256:21090af93f8324db2f0f0f4f38a7c60369d47d917e000c3423d89d2ae2883537",
    // elasticsearch 9.5.5. Labs build images/elasticsearch-nori on top of it.
    "ELASTICSEARCH_IMAGE" to "elasticsearch@sha256:764a939891119d97e130b4d5da196c330afb2338382ada9e1b9b7943ce6707df",
    // clickhouse-server 26.8 (LTS)
    "CLICKHOUSE_IMAGE" to "clickhouse/clickhouse-server@sha256:d426d2f612e1e189c90f9a3f8dcd85a03c6851273833817a296bb327e63aa7a7",
    // mysql 8.4.13 (LTS)
    "MYSQL_IMAGE" to "mysql@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902",
    // postgres 18.6, alpine
    "POSTGRES_IMAGE" to "postgres@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5",
    // apache/kafka 4.4.0, KRaft single node
    "KAFKA_IMAGE" to "apache/kafka@sha256:833b867a9585a141da455dc2b57431c65cbccb27f11c91f694444cca9f777e0f",
    // Built locally from images/jvm-runner. The tag changes whenever that directory changes.
    "JVM_RUNNER_IMAGE" to "cresvoid-lab/jvm-runner:1",
)

/** Compose variables for the learner's program when it runs as a service from the lab runner image. */
fun programEnvironment(programDir: File): Map<String, String> = mapOf("PROGRAM_DIR" to programDir.canonicalPath)

/** Exit code the engine reads as an experiment error, not as a failed invariant. */
val EXPERIMENT_ERROR = 2

fun requireDocker() {
    val probe = try {
        ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
            .redirectErrorStream(true)
            .start()
    } catch (_: java.io.IOException) {
        null
    }
    if (probe == null || !probe.waitFor(20, TimeUnit.SECONDS) || probe.exitValue() != 0) {
        System.err.println("Docker 가 실행 중이 아니다. Docker Desktop 을 켠 뒤 다시 실행한다.")
        exitProcess(EXPERIMENT_ERROR)
    }
}

/**
 * One compose project per experiment run. The project name carries the process id so parallel runs never share
 * containers, and published ports are ephemeral so they never collide.
 */
class ComposeStack(
    private val file: File,
    label: String,
    private val environment: Map<String, String> = emptyMap(),
) : AutoCloseable {
    val project: String = "cresvoid-" + label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') +
        "-" + ProcessHandle.current().pid()
    private val cleanup = Thread { down() }
    private var closed = false

    init {
        Runtime.getRuntime().addShutdownHook(cleanup)
    }

    fun up(waitSeconds: Int = 120) {
        val result = compose("up", "-d", "--wait", "--wait-timeout", waitSeconds.toString())
        if (result.code != 0) {
            System.err.println("서비스를 시작하지 못했다: ${result.output.takeLast(2000)}")
            close()
            exitProcess(EXPERIMENT_ERROR)
        }
    }

    /** Host port published for [containerPort] of [service]. */
    fun port(service: String, containerPort: Int): Int {
        val result = compose("port", service, containerPort.toString())
        return result.output.trim().substringAfterLast(':').toIntOrNull()
            ?: error("$service:$containerPort 의 포트를 찾지 못했다: ${result.output}")
    }

    fun exec(service: String, vararg command: String): String =
        compose("exec", "-T", service, *command).output

    /** Runs a one-off container of [service], for example a producer or a client that finishes on its own. */
    fun run(service: String, vararg command: String): Result = compose("run", "--rm", "-T", service, *command)

    /** Pauses or resumes a service, for experiments that simulate an outage. */
    fun pause(service: String) = compose("pause", service)

    fun unpause(service: String) = compose("unpause", service)

    override fun close() {
        down()
        try {
            Runtime.getRuntime().removeShutdownHook(cleanup)
        } catch (_: IllegalStateException) {
        }
    }

    @Synchronized
    private fun down() {
        if (closed) return
        closed = true
        compose("down", "-v", "--remove-orphans", "-t", "2")
    }

    class Result(val code: Int, val output: String)

    private fun compose(vararg arguments: String): Result {
        val process = ProcessBuilder(listOf("docker", "compose", "-p", project, "-f", file.absolutePath) + arguments)
            .redirectErrorStream(true)
            .apply { environment().putAll(LAB_IMAGES + environment) }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return Result(process.exitValue(), output)
    }
}
