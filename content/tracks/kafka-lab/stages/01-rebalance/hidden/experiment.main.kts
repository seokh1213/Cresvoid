@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class Receipt(val id: Int, val attempts: Int, val completed: Int, val latency: Long)
data class Snapshot(val counters: Map<String, Int>, val receipts: List<Receipt>)
data class Offsets(val end: Long, val committed: Long)

fun snapshot(client: HttpClient, base: String): Snapshot {
    val response = client.send(get(base, "/stats"), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200)
    val lines = response.body().lineSequence().filter { it.isNotBlank() }.toList()
    val counters = lines.filter { '=' in it }.associate {
        val (key, value) = it.split('=')
        key to value.toInt()
    }
    val receipts = lines.filter { ',' in it }.map {
        val fields = it.split(',')
        check(fields.size == 4)
        Receipt(fields[0].toInt(), fields[1].toInt(), fields[2].toInt(), fields[3].toLong())
    }
    check(counters.keys == setOf("accepted", "assignments", "commitFailures", "fatalErrors"))
    check(receipts.map { it.id }.distinct().size == receipts.size)
    check(receipts.all { it.id in 0..11 && it.attempts > 0 && it.completed in 0..1 && it.latency >= 0 })
    return Snapshot(counters, receipts)
}

fun control(client: HttpClient, base: String, path: String) {
    val response = client.send(post(base, path, "", Duration.ofSeconds(20)), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "$path failed: ${response.statusCode()}" }
}

fun offsets(stack: ComposeStack): Offsets {
    val end = stack.exec("kafka", "/opt/kafka/bin/kafka-get-offsets.sh", "--bootstrap-server", "kafka:9092",
        "--topic", "work-queue", "--time", "-1")
    val endOffset = end.lineSequence().firstOrNull { it.trim().startsWith("work-queue:0:") }
        ?.trim()?.substringAfterLast(':')?.toLongOrNull()
        ?: error("end offset query failed: $end")
    val output = stack.exec("kafka", "/opt/kafka/bin/kafka-consumer-groups.sh", "--bootstrap-server", "kafka:9092",
        "--group", "lab-consumer", "--describe")
    val row = output.lineSequence().map { it.trim().split(Regex("\\s+")) }
        .firstOrNull { it.size >= 6 && it[0] == "lab-consumer" && it[1] == "work-queue" && it[2] == "0" }
    if (row == null) {
        check("has no active members" in output || "does not exist" in output) { "group query failed: $output" }
        return Offsets(endOffset, 0)
    }
    check(row[4].toLong() == endOffset) { "inconsistent end offset queries: $output" }
    return Offsets(endOffset, if (row[3] == "-") 0 else row[3].toLong())
}

fun measure(stack: ComposeStack, base: String): Boolean {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
        val arrivals = steady(30, 0.4) { id -> post(base, "/messages", id.toString(), Duration.ofSeconds(20)) }
        val outcomes = runLoad(client, arrivals)
        val httpErrors = outcomes.count { it.status != 202 || it.error != null }
        control(client, base, "/start")
        // The restart remains an open-model arrival even while the consumer is busy.
        val handover = runLoad(client, listOf(Arrival(3500, post(base, "/restart", "", Duration.ofSeconds(20)))))
        check(handover.single().status == 200) { "restart did not complete" }
        val deadline = System.nanoTime() + Duration.ofSeconds(28).toNanos() - Duration.ofMillis(3500).toNanos()
        while (System.nanoTime() < deadline) {
            val current = snapshot(client, base)
            if (current.receipts.count { it.completed == 1 } == 12) {
                Thread.sleep(1000)
                break
            }
            Thread.sleep(250)
        }
        control(client, base, "/stop")
        val result = snapshot(client, base)
        val completed = result.receipts.count { it.completed == 1 }
        val maximumAttempts = result.receipts.maxOfOrNull { it.attempts } ?: 0
        val maximumLatency = result.receipts.filter { it.completed == 1 }.maxOfOrNull { it.latency } ?: 0
        val offsets = offsets(stack)
        metric("accepted", result.counters.getValue("accepted"))
        metric("completed", completed)
        metric("max_attempts", maximumAttempts)
        metric("assignments", result.counters.getValue("assignments"))
        metric("commit_failures", result.counters.getValue("commitFailures"))
        metric("max_latency_ms", maximumLatency)
        metric("end_offset", offsets.end)
        metric("committed_offset", offsets.committed)
        metric("http_errors", httpErrors)
        metric("fatal_errors", result.counters.getValue("fatalErrors"))
        return result.counters.getValue("accepted") == 12 && completed == 12 && maximumAttempts <= 2 &&
            result.counters.getValue("assignments") == 2 && result.counters.getValue("commitFailures") == 0 &&
            maximumLatency <= 22000 && offsets.end == 12L && offsets.committed == 12L &&
            httpErrors == 0 && result.counters.getValue("fatalErrors") == 0
    }
}

try {
    require(args.size == 2 && args[1] == "queued-work") { "usage: experiment.main.kts <absolute project> queued-work" }
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile)
    requireDocker()
    val port = freePort()
    val passed = ComposeStack(File("compose.yaml"), "kafka-rebalance",
        programEnvironment(program) + ("APP_PORT" to port.toString())).use { stack ->
        stack.up(150)
        val base = "http://127.0.0.1:$port"
        measure(stack, base)
    }
    exitProcess(if (passed) 0 else 1)
} catch (error: Exception) {
    System.err.println("experiment error: ${error.message}")
    exitProcess(2)
}
