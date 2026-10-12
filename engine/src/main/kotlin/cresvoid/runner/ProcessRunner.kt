package cresvoid.runner

import java.nio.file.Path
import java.time.Duration

const val STDOUT_LIMIT = 64 shl 20
const val STDERR_LIMIT = 1 shl 20

interface ProcessRunner {
    fun run(dir: Path, command: List<String>, input: ByteArray, timeout: Duration): ProcessResult

    fun stopOnInterrupt()
}

class ProcessResult(
    val stdout: ByteArray,
    val stderr: ByteArray,
    val exitCode: Int,
    val timedOut: Boolean,
    val truncated: Boolean,
    val duration: Duration,
    val startError: String?,
) {
    val succeeded: Boolean get() = startError == null && !timedOut && exitCode == 0

    fun describeExit(): String = when {
        startError != null -> "실행하지 못했습니다: $startError"
        timedOut -> "제한 시간을 넘었습니다"
        exitCode != 0 -> "종료 코드 $exitCode"
        else -> "정상 종료"
    }

    companion object {
        fun notStarted(reason: String) = ProcessResult(
            stdout = ByteArray(0),
            stderr = ByteArray(0),
            exitCode = -1,
            timedOut = false,
            truncated = false,
            duration = Duration.ZERO,
            startError = reason,
        )
    }
}
