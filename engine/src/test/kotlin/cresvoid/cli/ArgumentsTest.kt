package cresvoid.cli

import cresvoid.CresvoidError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private object TestCommand : Command {
    override val name = "test"
    override val usage = "cresvoid test <트랙> [-stage N] [-dir 경로] [-seed S]"

    override fun run(args: List<String>) = Unit
}

private val testFlags = listOf(stageFlag, TextFlag("dir"), IntegerFlag("seed"))

class ArgumentsTest : FunSpec({
    test("옵션은 한 줄표와 두 줄표, = 와 다음 인자 꼴을 모두 받는다") {
        val arguments = parseTrackArguments(TestCommand, listOf("mini", "--stage=2", "-dir", "work", "-seed", "-5"), testFlags)
        arguments.track shouldBe "mini"
        arguments.stage shouldBe "2"
        arguments.dir shouldBe "work"
        arguments.seed shouldBe -5L
    }
    test("seed 는 십진 정수만 받는다") {
        shouldThrow<CresvoidError> { parseTrackArguments(TestCommand, listOf("t", "-seed", "0x10"), testFlags) }
            .message shouldBe "-seed 에는 정수를 씁니다: 0x10"
    }
    test("빈 값은 주지 않은 것으로 본다") {
        parseTrackArguments(TestCommand, listOf("t", "-stage", ""), testFlags).stage shouldBe null
    }
    test("트랙 이름이 없거나 남는 인자가 있으면 알린다") {
        shouldThrow<CresvoidError> { parseTrackArguments(TestCommand, listOf("-stage", "1"), testFlags) }
            .message shouldBe "트랙 이름이 필요합니다. 예: cresvoid test search-engine"
        shouldThrow<CresvoidError> { parseTrackArguments(TestCommand, listOf("t", "x", "-stage", "1"), testFlags) }
            .message shouldBe "알 수 없는 인자입니다: x -stage 1"
        shouldThrow<CresvoidError> { parseTrackArguments(TestCommand, listOf("t", "--", "-stage"), testFlags) }
            .message shouldBe "알 수 없는 인자입니다: -stage"
    }
    test("모르는 옵션과 값이 빠진 옵션은 사용법과 함께 알린다") {
        shouldThrow<CresvoidError> { parseTrackArguments(TestCommand, listOf("t", "-x"), testFlags) }
            .message shouldBe "모르는 옵션입니다: -x\n사용법: ${TestCommand.usage}"
        shouldThrow<CresvoidError> { parseTrackArguments(TestCommand, listOf("t", "-stage"), testFlags) }
            .message shouldBe "-stage 에 값이 필요합니다\n사용법: ${TestCommand.usage}"
    }
    test("참·거짓 옵션은 값 없이 켜고 값을 주면 거절한다") {
        val flags = listOf(stageFlag, SwitchFlag("yes"))
        parseFlags(TestCommand, listOf("-yes"), flags).isSet("yes") shouldBe true
        parseFlags(TestCommand, listOf("--yes", "-stage", "2"), flags).text("stage") shouldBe "2"
        parseFlags(TestCommand, emptyList(), flags).isSet("yes") shouldBe false
        shouldThrow<CresvoidError> { parseFlags(TestCommand, listOf("-yes=false"), flags) }
            .message shouldBe "-yes 옵션은 값을 받지 않습니다"
        shouldThrow<CresvoidError> { parseFlags(TestCommand, listOf("-yes", "false"), flags) }
            .message shouldBe "알 수 없는 인자입니다: false"
    }
    test("-h 는 그 명령의 사용법을 묻는다") {
        shouldThrow<HelpRequested> { parseTrackArguments(TestCommand, listOf("-h"), testFlags) }.usage shouldBe TestCommand.usage
        shouldThrow<HelpRequested> { parseTrackArguments(TestCommand, listOf("t", "--help"), testFlags) }.usage shouldBe TestCommand.usage
    }
})
