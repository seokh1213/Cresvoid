package cresvoid.runner

import cresvoid.CresvoidError
import cresvoid.content.Stage
import java.time.Duration

private val diffTimeout: Duration = Duration.ofSeconds(30)
private val hunkHeader = Regex("""@@ -\d+(?:,(\d+))? \+\d+(?:,(\d+))? @@.*""")

data class ChangedLines(val added: Int, val removed: Int) {
    val total: Int get() = added + removed
}

fun starterDiff(stage: Stage, runner: ProcessRunner): String {
    val starter = stage.path.relativize(stage.starterDir).toString()
    val solution = stage.path.relativize(stage.solutionDir).toString()
    val command = listOf("diff", "-ruN", "-x", "build", "-x", ".DS_Store", starter, solution)
    val result = runner.run(stage.path, command, ByteArray(0), diffTimeout)
    if (result.startError != null || result.timedOut || result.exitCode !in 0..1) {
        throw CresvoidError("시작 코드와 참조 풀이를 비교하지 못했습니다 (${result.describeExit()}): ${stage.dir}\n${tailLines(result.stderr, 5).joinToString("\n")}")
    }
    return String(result.stdout, Charsets.UTF_8)
}

fun countChangedLines(unifiedDiff: String): ChangedLines {
    var added = 0
    var removed = 0
    var oldLinesLeft = 0
    var newLinesLeft = 0
    for (line in unifiedDiff.split("\n")) {
        if (oldLinesLeft <= 0 && newLinesLeft <= 0) {
            val hunk = hunkHeader.matchEntire(line) ?: continue
            oldLinesLeft = hunk.groupValues[1].ifEmpty { "1" }.toInt()
            newLinesLeft = hunk.groupValues[2].ifEmpty { "1" }.toInt()
            continue
        }
        when (line.firstOrNull()) {
            '-' -> {
                removed++
                oldLinesLeft--
            }
            '+' -> {
                added++
                newLinesLeft--
            }
            '\\' -> Unit
            else -> {
                oldLinesLeft--
                newLinesLeft--
            }
        }
    }
    return ChangedLines(added, removed)
}
