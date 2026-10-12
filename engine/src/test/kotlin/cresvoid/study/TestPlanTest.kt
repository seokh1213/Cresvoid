package cresvoid.study

import cresvoid.content.Stage
import cresvoid.content.Track
import cresvoid.content.TrackKind
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.io.path.Path

private val stages = (1..3).map { number ->
    Stage(number, "0$number-s", "단계", targetMinutes = 0, path = Path("0$number"), documentPath = Path("0$number/spec.md"))
}

class TestPlanTest : FunSpec({
    test("구현 트랙은 1단계부터, 버그 수정 트랙은 대상 단계만 테스트한다") {
        Track("t", Path("t"), "트랙", TrackKind.Build, "요약", stages).stagesToRun(stages[1]).map { it.dir } shouldBe listOf("01-s", "02-s")
        Track("t", Path("t"), "트랙", TrackKind.Fix, "요약", stages).stagesToRun(stages[1]).map { it.dir } shouldBe listOf("02-s")
        Track("t", Path("t"), "트랙", TrackKind.Reading, "요약", stages).stagesToRun(stages[1]) shouldBe emptyList()
    }
})
