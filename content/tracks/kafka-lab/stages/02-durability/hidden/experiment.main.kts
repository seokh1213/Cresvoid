@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class Partition(val leader: Int, val isr: Set<Int>)
data class WriteResult(val name: String, val id: String, val outcome: Outcome)
data class Observation(
    val baseline: List<WriteResult>,
    val degraded: List<WriteResult>,
    val records: Map<String, List<String>>,
    val recovered: Map<String, Partition>,
)
val names = listOf("quorum", "leader", "flexible", "single")
val beforeIds = (0..3).map { "b$it" }
val duringIds = (0..3).map { "d$it" }

class ObservationLog(program: File) {
    private val file = File(program, "build/durability-observations.txt")

    init {
        file.writeText("journal-handover\n")
    }

    fun append(section: String, body: String) {
        file.appendText("[$section]\n${body.ifEmpty { "(empty)" }}\n")
    }

    fun state(phase: String, partitions: Map<String, Partition>) {
        append("$phase /topology", names.joinToString("\n") { name ->
            val partition = partitions.getValue(name)
            "$name ${partition.leader} ${partition.isr.sorted().joinToString(",").ifEmpty { "-" }}"
        })
    }

    fun writes(phase: String, results: List<WriteResult>) {
        append("$phase writes", results.joinToString("\n") { result ->
            "${result.name}/${result.id} HTTP ${result.outcome.status} ${result.outcome.body}" +
                (result.outcome.error?.let { " ERROR $it" } ?: "")
        })
    }
}

fun request(client: HttpClient, base: String, path: String): String {
    val response = client.send(get(base, path, Duration.ofSeconds(25)), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "$path: ${response.statusCode()} ${response.body()}" }
    return response.body()
}

fun topology(client: HttpClient, base: String): Map<String, Partition> =
    request(client, base, "/topology").lineSequence().filter { it.isNotBlank() }.associate { line ->
        val fields = line.split(' ')
        check(fields.size == 3 && fields[0] in names) { "invalid topology: $line" }
        fields[0] to Partition(fields[1].toInt(), if (fields[2] == "-") emptySet() else fields[2].split(',').map { it.toInt() }.toSet())
    }.also { check(it.keys == names.toSet()) }

fun awaitTopology(client: HttpClient, base: String, ready: (Map<String, Partition>) -> Boolean): Map<String, Partition> {
    val deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos()
    var observed = emptyMap<String, Partition>()
    while (System.nanoTime() < deadline) {
        val current = runCatching { topology(client, base) }.getOrNull()
        if (current != null) {
            observed = current
            if (ready(observed)) return observed
        }
        Thread.sleep(500)
    }
    error("topology did not settle: $observed")
}

fun lifecycle(stack: ComposeStack, environment: Map<String, String>, vararg arguments: String) {
    val process = ProcessBuilder(listOf("docker", "compose", "-p", stack.project, "-f", File("compose.yaml").absolutePath) + arguments)
        .redirectErrorStream(true)
        .apply { environment().putAll(LAB_IMAGES + environment) }.start()
    try {
        check(process.waitFor(40, TimeUnit.SECONDS)) { "compose ${arguments.toList()} timed out" }
        val output = process.inputStream.bufferedReader().readText()
        check(process.exitValue() == 0) { "compose failed: $output" }
    } finally {
        if (process.isAlive) process.destroyForcibly().waitFor()
    }
}

fun writes(client: HttpClient, base: String, ids: List<String>): List<WriteResult> {
    val arrivals = names.flatMap { name ->
        steady(4, 1.0) { index -> post(base, "/send/$name/${ids[index]}", "", Duration.ofSeconds(25)) }
    }
    return runLoad(client, arrivals).map { outcome ->
        val fields = outcome.arrival.request.uri().path.split('/')
        WriteResult(fields[2], fields[3], outcome)
    }
}

fun mismatchCount(results: List<WriteResult>, degraded: Boolean): Int = results.count { result ->
    val reject = degraded && result.name == "quorum"
    result.outcome.status != (if (reject) 409 else 200) ||
        result.outcome.body != "${if (reject) "NOT_ENOUGH_REPLICAS" else "ACK"} ${result.id}"
}

fun readRecords(client: HttpClient, base: String, name: String): List<String> =
    request(client, base, "/records/$name").lineSequence().filter { it.isNotBlank() }.toList()

fun compareRecords(actual: List<String>, expected: List<String>): Int =
    (actual.toSet() - expected.toSet()).size + (expected.toSet() - actual.toSet()).size + actual.size - actual.distinct().size

fun awaitBaseline(client: HttpClient, base: String) {
    val deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos()
    while (System.nanoTime() < deadline) {
        if (names.all { readRecords(client, base, it) == beforeIds }) return
        Thread.sleep(250)
    }
    error("baseline records did not replicate")
}

fun measure(stack: ComposeStack, environment: Map<String, String>, base: String, log: ObservationLog): Boolean {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
        check(request(client, base, "/health") == "ready")
        val setup = client.send(post(base, "/setup", "", Duration.ofSeconds(30)), HttpResponse.BodyHandlers.ofString())
        check(setup.statusCode() == 200 && setup.body() == "configured") { "setup: ${setup.statusCode()} ${setup.body()}" }
        log.state("baseline", awaitTopology(client, base) { map -> names.all { map.getValue(it) == Partition(1, if (it == "single") setOf(1) else setOf(1, 2, 3)) } })
        val baseline = writes(client, base, beforeIds)
        log.writes("baseline", baseline)
        awaitBaseline(client, base)
        // An ISR barrier keeps the rejection count independent of broker failure-detection timing.
        lifecycle(stack, environment, "stop", "-t", "2", "kafka2", "kafka3")
        log.state("degraded", awaitTopology(client, base) { map -> map.values.all { it == Partition(1, setOf(1)) } })
        val degraded = writes(client, base, duringIds)
        log.writes("degraded", degraded)
        lifecycle(stack, environment, "kill", "-s", "SIGKILL", "kafka1")
        lifecycle(stack, environment, "start", "kafka2", "kafka3")
        val recovered = awaitTopology(client, base) { map ->
            names.filter { it != "single" }.all { name ->
                map.getValue(name).let { it.leader in 2..3 && it.isr == setOf(2, 3) }
            } && map.getValue("single").leader == -1
        }
        log.state("survivors", recovered)
        val records = names.filter { it != "single" }.associateWith { name ->
            readRecords(client, base, name).also { log.append("survivors /records/$name", it.joinToString("\n")) }
        }.toMutableMap()
        lifecycle(stack, environment, "start", "kafka1")
        log.state("restored", awaitTopology(client, base) { map -> names.all { map.getValue(it).isr == if (it == "single") setOf(1) else setOf(1, 2, 3) } })
        records["single"] = readRecords(client, base, "single")
        log.append("restored /records/single", records.getValue("single").joinToString("\n"))
        val receipts = request(client, base, "/receipts")
        log.append("final /receipts", receipts)
        return report(receipts, Observation(baseline, degraded, records, recovered))
    }
}

fun report(receiptBody: String, observation: Observation): Boolean {
    val (baseline, degraded, records, recovered) = observation
    val results = baseline + degraded
    val expectedReceipts = results.map { "${it.name} ${it.id} ${it.outcome.body.substringBefore(' ')}" }.sorted()
    val receipts = receiptBody.lineSequence().filter { it.isNotBlank() }.sorted().toList()
    val receiptErrors = compareRecords(receipts, expectedReceipts)
    val observationErrors = mismatchCount(baseline, false) + mismatchCount(degraded, true) + receiptErrors
    val recordErrors = names.sumOf { compareRecords(records.getValue(it), if (it == "single") beforeIds + duringIds else beforeIds) }
    val acknowledged = results.filter { it.outcome.status == 200 && it.outcome.body == "ACK ${it.id}" }
    val lost = acknowledged.count { it.id !in records.getValue(it.name) }
    val httpErrors = results.count {
        it.outcome.error != null || it.outcome.status !in setOf(200, 409) ||
            it.outcome.body != "${if (it.outcome.status == 200) "ACK" else "NOT_ENOUGH_REPLICAS"} ${it.id}"
    }
    val baselineAcks = baseline.count { it.outcome.status == 200 }
    val degradedAcks = degraded.count { it.outcome.status == 200 }
    val rejections = degraded.count { it.outcome.status == 409 }
    val replicaErrors = degraded.count { it.outcome.body == "NOT_ENOUGH_REPLICAS ${it.id}" }
    val unavailable = if (recovered.getValue("single").leader == -1) 1 else 0
    metric("baseline_acks", baselineAcks)
    metric("degraded_acks", degradedAcks)
    metric("degraded_rejections", rejections)
    metric("replica_errors", replicaErrors)
    metric("lost_acks", lost)
    metric("retained_acks", acknowledged.size - lost)
    metric("unavailable_partitions", unavailable)
    metric("http_errors", httpErrors)
    metric("observation_mismatches", observationErrors)
    metric("record_mismatches", recordErrors)
    return baselineAcks == 16 && degradedAcks == 12 && rejections == 4 && replicaErrors == 4 &&
        lost == 8 && acknowledged.size - lost == 20 && unavailable == 1 && httpErrors == 0 &&
        observationErrors == 0 && recordErrors == 0
}

try {
    require(args.size == 2 && args[1] == "journal-handover") { "usage: experiment.main.kts <absolute project> journal-handover" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile)
    requireDocker()
    val port = freePort()
    val environment = programEnvironment(program) + ("APP_PORT" to port.toString())
    val log = ObservationLog(program)
    val passed = ComposeStack(File("compose.yaml"), "kafka-durability", environment).use { stack ->
        log.append("starting", "project=${stack.project} base=http://127.0.0.1:$port")
        stack.up(180)
        try {
            measure(stack, environment, "http://127.0.0.1:$port", log)
        } catch (error: Exception) {
            log.append("ERROR", error.message ?: error.javaClass.simpleName)
            val logs = ProcessBuilder("docker", "logs", "${stack.project}-app-1").redirectErrorStream(true).start()
            System.err.println(logs.inputStream.bufferedReader().readText().takeLast(4000))
            logs.waitFor()
            throw error
        }
    }
    log.append("finished", if (passed) "PASS" else "INVARIANT_FAILED")
    exitProcess(if (passed) 0 else 1)
} catch (error: Exception) {
    System.err.println("experiment error: ${error.message}")
    exitProcess(2)
}
