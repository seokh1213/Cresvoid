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
private val groupStopGrace: Duration = Duration.ofSeconds(2)

// JDK 로는 자식의 프로세스 그룹을 만들 수 없어 bash 작업 제어(set -m)로 만든다
private const val PROCESS_GROUP_SCRIPT = "exec 3>&2 2>/dev/null; " +
    "trap 'kill -KILL -- -\"\$group\"; exit 143' TERM; " +
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
        process.destroy()
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
