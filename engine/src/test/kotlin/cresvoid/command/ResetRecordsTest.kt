package cresvoid.command

import cresvoid.progress.Progress
import cresvoid.progress.QuizProgress
import cresvoid.progress.StageProgress
import cresvoid.progress.TrackProgress
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val recordedAt: OffsetDateTime = OffsetDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneOffset.ofHours(9))

class ResetRecordsTest : FunSpec({
    test("지울 내용은 남아 있는 기록만 적는다") {
        val stage = StageProgress(
            solutionDir = "practice/patch/01",
            openedAt = recordedAt,
            quiz = QuizProgress(firstCorrect = 3, firstTotal = 5, completedAt = recordedAt),
            passedAt = recordedAt,
            reviewedAt = recordedAt,
        )
        val progress = Progress(
            mapOf(
                "patch" to TrackProgress(stages = mapOf("02" to StageProgress(), "01" to stage)),
                "mini" to TrackProgress(solutionDir = "practice/mini"),
            ),
        )
        withClue("지울 내용") { describeRecords(progress) shouldBe listOf(
                "mini: 작업 공간 선택(practice/mini)",
                "patch 01: 작업 공간 선택(practice/patch/01), 읽기, 문항 첫 시도 3/5, 문항 완료, 통과 2026-10-08, 복습 2026-10-08",
                "patch 02: 빈 기록",
            ) }
    }
})
