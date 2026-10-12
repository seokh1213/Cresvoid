package cresvoid.content

import cresvoid.CresvoidError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.path.Path

class TrackKindTest : FunSpec({
    test("트랙 종류마다 완료 조건, 문항 규칙, 작업 공간, 테스트 범위가 다르다") {
        TrackKind.Build.completion shouldBe Completion.TESTS_PASSED
        TrackKind.Code.completion shouldBe Completion.QUIZ_COMPLETED
        TrackKind.Code.quizRules.shortQuestions shouldBe ShortQuestions.INPUT_REQUIRED
        TrackKind.Build.quizRules.shortQuestions shouldBe ShortQuestions.INPUT_OPTIONAL
        TrackKind.Reading.quizRules.exactChoiceCount shouldBe 5
        TrackKind.Fix.quizRules shouldBe null
        TrackKind.Fix.workspaceScope shouldBe WorkspaceScope.STAGE
        TrackKind.Build.testScope shouldBe TestScope.CUMULATIVE
        TrackKind.Fix.testScope shouldBe TestScope.TARGET_STAGE
        TrackKind.all.map { it.id } shouldBe listOf("build", "reading", "code", "fix")
    }
    test("단계는 번호나 디렉터리 이름으로 고른다") {
        val stages = listOf("01-a", "02-b").mapIndexed { index, dir ->
            Stage(index + 1, dir, dir, targetMinutes = 0, path = Path(dir), documentPath = Path(dir))
        }
        val track = Track("t", Path("t"), "트랙", TrackKind.Build, "요약", stages)
        track.stageBySelector("2").dir shouldBe "02-b"
        track.stageBySelector("02").dir shouldBe "02-b"
        track.stageBySelector("01-a").dir shouldBe "01-a"
        shouldThrow<CresvoidError> { track.stageBySelector("3") }.message shouldBe "단계 번호는 1~2 입니다"
        shouldThrow<CresvoidError> { track.stageBySelector("zz") }.message shouldBe "없는 단계입니다: zz"
    }
})
