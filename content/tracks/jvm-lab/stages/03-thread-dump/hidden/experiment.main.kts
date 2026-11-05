@file:Import("../../../../../lab-env/load.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class Transfer(val from: String, val to: String, val amount: Int) {
    fun path(pair: String? = null): String =
        "/transfer?from=$from&to=$to&amount=$amount" + (pair?.let { "&pair=$it" } ?: "")
}

data class Scenario(val pair: List<Transfer>, val regular: List<Transfer>, val intervalMillis: Long)

fun scenario(name: String): Scenario = when (name) {
    "paired-arrivals" -> Scenario(
        listOf(Transfer("A", "B", 7), Transfer("B", "A", 3)),
        List(24) { if (it % 2 == 0) Transfer("A", "B", 2) else Transfer("B", "A", 1) },
        50L
    )
    "mixed-arrivals" -> Scenario(
        listOf(Transfer("B", "A", 11), Transfer("A", "B", 5)),
        List(48) { if (it % 3 == 0) Transfer("B", "A", 3) else Transfer("A", "B", 2) },
        25L
    )
    else -> error("알 수 없는 시나리오")
}

fun expectedBalances(transfers: List<Transfer>): List<Int> {
    val delta = transfers.sumOf { if (it.from == "A") -it.amount else it.amount }
    return listOf(10_000 + delta, 10_000 - delta)
}

fun balanceErrors(client: HttpClient, base: String, transfers: List<Transfer>): Int {
    val response = try {
        client.send(get(base, "/balances", Duration.ofSeconds(2)), HttpResponse.BodyHandlers.ofString())
    } catch (_: java.io.IOException) {
        return 2
    }
    if (response.statusCode() != 200) return 2
    val match = Regex("A=(-?[0-9]+) B=(-?[0-9]+)").matchEntire(response.body()) ?: return 2
    val balances = match.groupValues.drop(1).map { it.toIntOrNull() }
    return balances.zip(expectedBalances(transfers)).count { (actual, expected) -> actual != expected }
}

fun deadlockCycles(pid: Long): Int {
    val output = File.createTempFile("thread-dump-", ".txt")
    val jcmd = File(System.getProperty("java.home"), "bin/jcmd")
    var command: Process? = null
    try {
        command = ProcessBuilder(jcmd.absolutePath, pid.toString(), "Thread.print", "-l")
            .redirectErrorStream(true).redirectOutput(output).start()
        check(command.waitFor(30, TimeUnit.SECONDS)) { "jcmd 제한 시간 초과" }
        check(command.exitValue() == 0) { "jcmd 실행 실패" }
        val dump = output.readText()
        check("Full thread dump" in dump && "Locked ownable synchronizers" in dump) { "불완전한 스레드 덤프" }
        return Regex("Found ([0-9]+) deadlocks?\\.").findAll(dump).lastOrNull()
            ?.groupValues?.get(1)?.toInt() ?: 0
    } finally {
        command?.let { process ->
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
        }
        output.delete()
    }
}

fun arrivals(base: String, transfers: List<Transfer>, spacing: Long, pair: String? = null): List<Arrival> =
    transfers.mapIndexed { index, transfer ->
        Arrival(index * spacing, post(base, transfer.path(pair), "", Duration.ofSeconds(8)))
    }

fun report(outcomes: List<Outcome>, balanceErrors: Int, cycles: Int, requested: Int): Int {
    val completed = outcomes.count { it.error == null && it.status == 200 && it.body == "OK" }
    val stalled = outcomes.count { it.error != null }
    val badResponses = outcomes.size - completed - stalled
    metric("requested", requested)
    metric("completed", completed)
    metric("stalled", stalled)
    metric("bad_responses", badResponses)
    metric("balance_errors", balanceErrors)
    metric("deadlock_cycles", cycles)
    metric("p99_ms", percentile(outcomes.map { it.elapsedMillis }, 0.99))
    return if (outcomes.size == requested && completed == requested && stalled == 0 &&
        badResponses == 0 && balanceErrors == 0 && cycles == 0) 0 else 1
}

fun experiment(programDir: File, scenario: Scenario): Int {
    require(programDir.isAbsolute && File(programDir, "run.sh").canExecute())
    val basePort = freePort()
    val base = "http://127.0.0.1:$basePort"
    val program = Program(programDir, mapOf("PORT" to basePort.toString()), base)
    try {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            val health = client.send(get(base, "/health"), HttpResponse.BodyHandlers.ofString())
            val pid = Regex("READY ([0-9]+)").matchEntire(health.body())
                ?.groupValues?.get(1)?.toLongOrNull() ?: error("PID 응답 오류")
            require(pid > 0)
            val paired = runLoad(client, arrivals(base, scenario.pair, 0, "probe"))
            val pairBalanceErrors = balanceErrors(client, base, scenario.pair)
            val pairCycles = deadlockCycles(pid)
            val regular = runLoad(client, arrivals(base, scenario.regular, scenario.intervalMillis))
            val allTransfers = scenario.pair + scenario.regular
            val finalBalanceErrors = balanceErrors(client, base, allTransfers)
            val finalCycles = deadlockCycles(pid)
            return report(paired + regular, pairBalanceErrors + finalBalanceErrors,
                maxOf(pairCycles, finalCycles), allTransfers.size)
        }
    } finally {
        program.close()
    }
}

val result = try {
    require(args.size == 2) { "프로그램 절대 경로와 시나리오가 필요하다" }
    experiment(File(args[0]), scenario(args[1]))
} catch (error: Exception) {
    System.err.println("실험 오류: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(result)
