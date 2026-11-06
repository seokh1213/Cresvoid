@file:Import("../../../../../lab-env/load.main.kts")
@file:Import("../../../../../lab-env/docker.main.kts")

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class Phase(val outcomes: List<Outcome>) {
    val successes = outcomes.count { it.status == 200 && it.body == "7" }
    val timeouts = outcomes.count { it.status == 503 && it.body == "metadata-timeout" }
    val p95 = percentile(outcomes.map { it.elapsedMillis }, 0.95)
}

class Observation(private val stack: ComposeStack, private val base: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private var controlsOk = true

    fun count(condition: String): Int {
        val output = stack.exec("mysql", "env", "MYSQL_PWD=lab-local", "mysql", "-uroot", "-N", "-B", "-e", condition)
        return output.trim().toIntOrNull() ?: error("MySQL observation failed: ${output.take(160)}")
    }

    private fun tableLocks(condition: String): Int = count(
        "SELECT COUNT(*) FROM performance_schema.metadata_locks WHERE OBJECT_SCHEMA='lab' AND OBJECT_NAME='items' AND $condition"
    )

    private fun send(request: HttpRequest): HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

    private fun control(path: String, expected: Int, body: String) {
        val response = send(post(base, path, "", Duration.ofSeconds(10)))
        controlsOk = response.statusCode() == expected && response.body() == body && controlsOk
    }

    private fun state(): String {
        val response = send(get(base, "/state"))
        controlsOk = response.statusCode() == 200 && controlsOk
        return response.body()
    }

    private fun phase(size: Int): Phase = Phase(runLoad(client, steady(4, size / 4.0) { get(base, "/item", Duration.ofSeconds(15)) }))

    private fun await(seconds: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos()
        do {
            if (predicate()) return true
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        return false
    }

    fun run(mode: String): Boolean {
        val health = send(get(base, "/health"))
        controlsOk = health.statusCode() == 200 && health.body() == "ready"
        controlsOk = state() == "IDLE" && controlsOk
        val baseline = phase(8)
        control("/hold", 200, "held")
        control("/hold", 409, "invalid-state")
        control("/release?mode=invalid", 400, "invalid-mode")
        val held = phase(8)
        control("/ddl", 202, "started")
        var pending = false
        await(10) {
            pending = tableLocks("LOCK_TYPE='EXCLUSIVE' AND LOCK_STATUS='PENDING'") > 0 &&
                tableLocks("LOCK_TYPE='SHARED_READ' AND LOCK_STATUS='GRANTED'") > 0
            pending || state() != "RUNNING"
        }
        control("/ddl", 409, "invalid-state")
        if (pending) controlsOk = state() == "RUNNING" && controlsOk
        val blocked: Phase
        var queued = 0
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val load = executor.submit<Phase> { phase(12) }
            await(4) {
                queued = maxOf(queued, tableLocks("LOCK_TYPE='SHARED_READ' AND LOCK_STATUS='PENDING'"))
                load.isDone
            }
            blocked = load.get(25, TimeUnit.SECONDS)
        }
        val began = System.nanoTime()
        control("/release?mode=$mode", 200, "released")
        val finished = await(12) { state() == "DONE" }
        val recoveryMillis = (System.nanoTime() - began) / 1e6
        val recovered = phase(12)
        control("/release?mode=$mode", 200, "released")
        control("/ddl", 409, "invalid-state")
        controlsOk = state() == "DONE" && controlsOk
        val changed = count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='lab' AND table_name='items' AND column_name='marker'")
        val remainingLocks = tableLocks("1=1")
        metric("baseline_ok", baseline.successes)
        metric("held_ok", held.successes)
        metric("ddl_pending", if (pending) 1 else 0)
        metric("queued_reads", queued)
        metric("blocked_errors", blocked.timeouts)
        metric("blocked_p95_ms", blocked.p95)
        metric("recovered_ok", recovered.successes)
        metric("recovery_ms", recoveryMillis)
        metric("schema_changed", if (finished) changed else 0)
        metric("locks_after", remainingLocks)
        return controlsOk && baseline.successes == 8 && held.successes == 8 && pending && queued > 0 &&
            blocked.timeouts == 12 && blocked.p95 in 1500.0..15000.0 && recovered.successes == 12 &&
            finished && recoveryMillis <= 15000 && changed == 1 && remainingLocks == 0
    }
}

fun experiment(): Boolean {
    require(args.size == 2)
    val program = File(args[0])
    require(program.isAbsolute && File(program, "build/app.jar").isFile)
    val mode = when (args[1]) {
        "first-window" -> "commit"
        "second-window" -> "rollback"
        else -> error("unknown scenario")
    }
    requireDocker()
    val port = freePort()
    ComposeStack(File("compose.yaml"), "db-mdl", programEnvironment(program) + ("APP_PORT" to port.toString())).use { stack ->
        stack.up(150)
        val base = "http://127.0.0.1:${stack.port("app", 8080)}"
        return Observation(stack, base).run(mode)
    }
}

val status = try {
    if (experiment()) 0 else 1
} catch (error: Exception) {
    System.err.println("Experiment error: ${error.javaClass.simpleName}: ${error.message}")
    2
}
exitProcess(status)
