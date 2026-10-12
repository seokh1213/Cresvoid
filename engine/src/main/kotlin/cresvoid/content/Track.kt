package cresvoid.content

import cresvoid.CresvoidError
import java.nio.file.Path

data class Track(
    val id: String,
    val path: Path,
    val title: String,
    val kind: TrackKind,
    val summary: String,
    val stages: List<Stage>,
) {
    val startersDir: Path get() = path.resolve("starters")

    fun stageBySelector(selector: String): Stage {
        val number = selector.toIntOrNull()
            ?: return stages.firstOrNull { it.dir == selector } ?: throw CresvoidError("없는 단계입니다: $selector")
        if (number !in 1..stages.size) throw CresvoidError("단계 번호는 1~${stages.size} 입니다")
        return stages[number - 1]
    }
}

data class Stage(
    val number: Int,
    val dir: String,
    val title: String,
    val targetMinutes: Int,
    val path: Path,
    val documentPath: Path,
) {
    val hiddenDir: Path get() = path.resolve("hidden")
    val quizPath: Path get() = hiddenDir.resolve("quiz.json")
    val testsPath: Path get() = hiddenDir.resolve("tests.json")
    val solutionDir: Path get() = hiddenDir.resolve("solution")
    val starterDir: Path get() = path.resolve("starter")
    val codeDir: Path get() = path.resolve("code")
}
