package cresvoid.runner

import cresvoid.content.LabScenario
import cresvoid.content.Stage
import java.nio.file.Path

private const val MAX_METRICS = 10
private val metricName = Regex("[a-zA-Z][a-zA-Z0-9_.-]*")

data class LabMetric(val name: String, val value: Double)

sealed interface LabOutcome {
    data object Passed : LabOutcome
    data object InvariantFailed : LabOutcome
    data class Invalid(val reason: String) : LabOutcome
}

data class LabRun(val outcome: LabOutcome, val metrics: List<LabMetric>)

class LabHarness(private val runner: ProcessRunner) {
    fun run(stage: Stage, programDir: Path, scenario: LabScenario): LabRun {
        val result = runner.run(
            stage.hiddenDir,
            listOf("kotlin", stage.experimentPath.toString(), programDir.toAbsolutePath().toString(), scenario.name),
            ByteArray(0),
            scenario.timeout,
        )
        if (result.startError != null) return LabRun(LabOutcome.Invalid("실험을 시작하지 못했습니다"), emptyList())
        if (result.timedOut) return LabRun(LabOutcome.Invalid("제한 시간 ${scenario.timeoutSec}초를 넘었습니다"), emptyList())
        if (result.truncated) return LabRun(LabOutcome.Invalid("지표 출력 제한을 넘었습니다"), emptyList())
        val metrics = parseMetrics(result.stdout) ?: return LabRun(LabOutcome.Invalid("실험 지표 출력 형식이 잘못되었습니다"), emptyList())
        val outcome = when (result.exitCode) {
            0 -> LabOutcome.Passed
            1 -> LabOutcome.InvariantFailed
            else -> LabOutcome.Invalid("실험을 완료하지 못했습니다 (종료 코드 ${result.exitCode})")
        }
        return LabRun(outcome, metrics)
    }
}

private fun parseMetrics(stdout: ByteArray): List<LabMetric>? {
    val text = stdout.toString(Charsets.UTF_8).trimEnd('\n', '\r')
    if (text.isEmpty()) return null
    val lines = text.split('\n')
    if (lines.size > MAX_METRICS) return null
    val seen = mutableSetOf<String>()
    return lines.map { line ->
        val parts = line.trimEnd('\r').split(' ')
        if (parts.size != 3 || parts[0] != "METRIC" || !metricName.matches(parts[1]) || !seen.add(parts[1])) return null
        val value = parts[2].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        LabMetric(parts[1], value)
    }
}
