package cresvoid.runner

import cresvoid.terminal.handleSignal
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private val outputDrainGrace: Duration = Duration.ofSeconds(2)
// lab 실험은 종료 훅에서 Docker 묶음을 내리므로 멈춘 그룹에 정리할 시간을 준 뒤 강제로 끝낸다
private val groupStopGrace: Duration = Duration.ofSeconds(30)

// JDK 로는 자식의 프로세스 그룹을 만들 수 없어 bash 작업 제어(set -m)로 만든다.
// TERM 은 그룹에 넘기고 그룹이 스스로 끝날 때까지 기다린다. KILL 은 종료 훅을 건너뛰기 때문이다.
private const val PROCESS_GROUP_SCRIPT = "exec 3>&2 2>/dev/null; " +
    "trap 'kill -TERM -- -\"\$group\"; wait \"\$group\"; " +
    "while kill -0 -- -\"\$group\"; do sleep 0.1; done; exit 143' TERM; " +
    "set -m; " +
    "\"\$@\" 2>&3 3>&- & group=\$!; " +
    "trap '' INT QUIT HUP; " +
    "exec 0<&- 1>&- 3>&-; " +
    "wait \"\$group\"; status=\$?; " +
    "kill -KILL -- -\"\$group\"; " +
    "exit \"\$status\""

class SystemProcessRunner : ProcessRunner {
    private val lock = Any()
    private val running = mutableSetOf<Process>()

    override fun run(dir: Path, command: List<String>, input: ByteArray, timeout: Duration): ProcessResult {
        val started = System.nanoTime()
        val builder = ProcessBuilder(listOf("bash", "--posix", "-c", PROCESS_GROUP_SCRIPT, "cresvoid") + command)
            .directory(dir.toFile())
        builder.environment().remove("KOTLIN_RUNNER")
        val process = try {
            start(builder)
        } catch (error: IOException) {
            return ProcessResult.notStarted(error.message ?: error.toString())
        }
        try {
            val stdout = OutputCollector(process.inputStream, STDOUT_LIMIT)
            val stderr = OutputCollector(process.errorStream, STDERR_LIMIT)
            feedInput(process, input)
            val exited = process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)
            if (!exited) stop(process)
            process.waitFor()
            val stdoutBytes = stdout.collected(outputDrainGrace)
            val stderrBytes = stderr.collected(outputDrainGrace)
            return ProcessResult(
                stdout = stdoutBytes,
                stderr = stderrBytes,
                exitCode = process.exitValue(),
                timedOut = !exited,
                truncated = stdout.truncated,
                duration = Duration.ofNanos(System.nanoTime() - started),
                startError = null,
            )
        } finally {
            finish(process)
        }
    }

    override fun stopOnInterrupt() {
        for (name in listOf("INT", "TERM", "HUP")) {
            handleSignal(name) { signal -> killAllAndExit(128 + signal.number) }
        }
    }

    private fun start(builder: ProcessBuilder): Process = synchronized(lock) {
        builder.start().also { running += it }
    }

    private fun stop(process: Process) {
        val descendants = process.descendants().toList()
        // Process.destroy() 는 엔진 쪽 파이프도 닫아서 정리하며 출력하는 자식이 SIGPIPE 로 죽는다. 핸들로 신호만 보내고 출력은 계속 읽는다.
        process.toHandle().destroy()
        if (!process.waitFor(groupStopGrace.toMillis(), TimeUnit.MILLISECONDS)) process.destroyForcibly()
        descendants.forEach(ProcessHandle::destroyForcibly)
    }

    private fun finish(process: Process) {
        synchronized(lock) {
            if (running.remove(process) && process.isAlive) stop(process)
        }
    }

    private fun killAllAndExit(code: Int): Nothing {
        synchronized(lock) {
            running.forEach(::stop)
            System.err.println("\n중단했습니다.")
            exitProcess(code)
        }
    }
}

private fun feedInput(process: Process, input: ByteArray) {
    thread(isDaemon = true, name = "cresvoid-stdin") {
        try {
            process.outputStream.use { it.write(input) }
        } catch (_: IOException) {
        }
    }
}

private class OutputCollector(private val stream: InputStream, private val limit: Int) {
    private val buffer = ByteArrayOutputStream()

    @Volatile
    var truncated = false
        private set

    private val reader = thread(isDaemon = true, name = "cresvoid-output") { drain() }

    private fun drain() {
        val chunk = ByteArray(64 * 1024)
        try {
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) return
                synchronized(buffer) {
                    val room = limit - buffer.size()
                    if (count > room) {
                        truncated = true
                        if (room > 0) buffer.write(chunk, 0, room)
                    } else {
                        buffer.write(chunk, 0, count)
                    }
                }
            }
        } catch (_: IOException) {
        }
    }

    fun collected(grace: Duration): ByteArray {
        reader.join(grace.toMillis())
        return synchronized(buffer) { buffer.toByteArray() }
    }
}
