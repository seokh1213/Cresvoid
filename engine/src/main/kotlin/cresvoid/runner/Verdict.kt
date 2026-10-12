package cresvoid.runner

import cresvoid.terminal.formatElapsed
import java.time.Duration

class CaseInput(val input: ByteArray, val seed: Long, val generated: Boolean)

enum class FailureKind { START, TIMEOUT, TRUNCATED, EXIT, OUTPUT }

sealed interface Verdict {
    data object Passed : Verdict

    class Failed(
        val kind: FailureKind,
        val reason: String,
        val diffIndex: Int = 0,
        val actual: List<String> = emptyList(),
    ) : Verdict
}

fun judge(result: ProcessResult, expected: List<String>, timeout: Duration): Verdict {
    when {
        result.startError != null ->
            return Verdict.Failed(FailureKind.START, "run.sh 를 실행하지 못했습니다: ${result.startError}")
        result.timedOut ->
            return Verdict.Failed(FailureKind.TIMEOUT, "제한 시간 ${formatElapsed(timeout)}를 넘었습니다")
        result.truncated ->
            return Verdict.Failed(FailureKind.TRUNCATED, "표준 출력이 64MB 를 넘었습니다")
        result.exitCode != 0 ->
            return Verdict.Failed(FailureKind.EXIT, "프로그램이 종료 코드 ${result.exitCode} 로 끝났습니다")
    }
    val actual = normalizeOutput(result.stdout)
    val diffIndex = firstDifference(expected, actual) ?: return Verdict.Passed
    return Verdict.Failed(FailureKind.OUTPUT, "출력이 기대와 다릅니다", diffIndex, actual)
}
