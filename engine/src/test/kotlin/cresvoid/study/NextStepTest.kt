package cresvoid.study

import cresvoid.content.Stage
import cresvoid.content.Track
import cresvoid.content.TrackKind
import cresvoid.progress.Progress
import cresvoid.progress.QuizProgress
import cresvoid.progress.StageProgress
import cresvoid.progress.TrackProgress
import cresvoid.project.Project
import cresvoid.withTempDir
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val now: OffsetDateTime = OffsetDateTime.of(2026, 10, 8, 10, 0, 0, 0, ZoneOffset.ofHours(9))

private fun trackOf(kind: TrackKind, root: Path): Track {
    val stages = listOf("01-first", "02-second").mapIndexed { index, dir ->
        val path = root.resolve("stages").resolve(dir)
        Stage(index + 1, dir, dir, targetMinutes = 0, path = path, documentPath = path.resolve(kind.documentName))
    }
    return Track("t", root, "트랙", kind, "요약", stages)
}

private val read = StageProgress(openedAt = now, lastReadAt = now)
private val quizDone = read.copy(quiz = QuizProgress(firstCorrect = 1, firstTotal = 1, completedAt = now))
private val passed = quizDone.copy(passedAt = now.minusDays(20), passedDir = "practice/t")

private fun progressOf(solutionDir: String?, vararg stages: Pair<String, StageProgress>) =
    Progress(mapOf("t" to TrackProgress(solutionDir = solutionDir, stages = mapOf(*stages))))

class NextStepTest : FunSpec({
    test("구현 트랙은 작업 공간, 읽기, 문항, 테스트 순서로 안내한다") {
        withTempDir { root ->
            val track = trackOf(TrackKind.Build, root)
            fun stepFor(progress: Progress) = TrackState(Project(root), track, progress).nextStep(now)
            val (first, second) = track.stages
            stepFor(Progress()) shouldBe NextStep.CreateWorkspace(stage = null)
            stepFor(progressOf("practice/t")) shouldBe NextStep.Read(first)
            stepFor(progressOf("practice/t", first.dir to read)) shouldBe NextStep.SolveQuiz(first)
            stepFor(progressOf("practice/t", first.dir to quizDone)) shouldBe NextStep.PassTests(first)
            stepFor(progressOf("practice/t", first.dir to passed, second.dir to quizDone)) shouldBe NextStep.PassTests(second)
        }
    }
    test("모든 단계를 통과하고 2주가 지나면 새 작업 공간에서 복습한다") {
        withTempDir { root ->
            val track = trackOf(TrackKind.Build, root)
            val (first, second) = track.stages
            Files.createDirectories(root.resolve("practice/t"))
            val allPassed = progressOf("practice/t", first.dir to passed, second.dir to passed)
            TrackState(Project(root), track, allPassed).nextStep(now) shouldBe NextStep.Review(first, inNewWorkspace = true)
        }
    }
    test("버그 수정 트랙은 단계마다 작업 공간을 만들고 읽은 뒤 테스트하며 독해 트랙은 문항을 마치면 끝난다") {
        withTempDir { root ->
            val fix = trackOf(TrackKind.Fix, root)
            val first = fix.stages.first()
            fun fixStepFor(stage: StageProgress) = TrackState(Project(root), fix, progressOf(null, first.dir to stage)).nextStep(now)
            TrackState(Project(root), fix, Progress()).nextStep(now) shouldBe NextStep.CreateWorkspace(first)
            fixStepFor(StageProgress(solutionDir = "practice/t/01-first")) shouldBe NextStep.Read(first)
            fixStepFor(read.copy(solutionDir = "practice/t/01-first")) shouldBe NextStep.PassTests(first)
            val reading = trackOf(TrackKind.Reading, root)
            val done = progressOf(null, *reading.stages.map { it.dir to quizDone }.toTypedArray())
            TrackState(Project(root), reading, done).nextStep(now) shouldBe NextStep.Finished
        }
    }
})
