package cresvoid.command

import cresvoid.command.QuizScreenState.Mode
import cresvoid.command.QuizScreenState.Outcome
import cresvoid.content.Question
import cresvoid.content.QuestionType
import cresvoid.study.ChoiceOrder
import cresvoid.terminal.Key
import cresvoid.terminal.ScreenSize
import cresvoid.terminal.displayWidth
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun choice(id: String, answer: Int, context: String = "") = Question(
    id = id,
    type = QuestionType.CHOICE,
    prompt = "$id 발문",
    context = context,
    choices = listOf("가 선지", "나 선지", "다 선지", "라 선지"),
    answer = answer,
    input = emptyList(),
    answers = emptyList(),
    refs = listOf("1"),
    quote = "핵심",
    explanation = "해설",
)

private val short = Question(
    id = "q3",
    type = QuestionType.SHORT,
    prompt = "출력은?",
    context = "",
    choices = emptyList(),
    answer = 0,
    input = listOf("ADD 1"),
    answers = listOf("값 1"),
    refs = listOf("2"),
    quote = "핵심",
    explanation = "해설",
)

/** q1 shows choices in reverse; q2 keeps quiz.json order. */
private fun state() = QuizScreenState(
    listOf(choice("q1", 1), choice("q2", 2), short),
    listOf(ChoiceOrder.of(listOf(3, 2, 1, 0)), ChoiceOrder.identity(4), ChoiceOrder.identity(0)),
)

private fun QuizScreenState.press(vararg keys: Key): Outcome = keys.map(::handle).last()

private fun type(text: String): Array<Key> = text.codePoints().toArray().map { Key.Text(String(Character.toChars(it))) }.toTypedArray()

private fun plain(line: String) = line.replace(Regex("\u001b\\[[0-9;]*m"), "")

class QuizScreenTest : FunSpec({
    test("섞인 화면 번호로 고른 답을 quiz.json 번호로 돌려준다") {
        val state = state()
        state.press(Key.Text("4"), Key.Enter) shouldBe Outcome.CONTINUE
        state.press(Key.Down, Key.Enter, *type("값 1"), Key.Enter)
        state.mode shouldBe Mode.REVIEW
        state.press(Key.Enter) shouldBe Outcome.SUBMIT
        state.responses() shouldBe listOf("1", "2", "값 1")
    }

    test("답하지 않은 문항이 있으면 제출하지 않고 그 문항으로 간다") {
        val state = state()
        state.press(Key.Tab, Key.Tab, Key.Tab)
        state.mode shouldBe Mode.REVIEW
        state.press(Key.Enter) shouldBe Outcome.CONTINUE
        state.mode shouldBe Mode.QUESTION
        state.current shouldBe 0
    }

    test("문항을 오가도 고른 답과 입력 중인 답이 남는다") {
        val state = state()
        state.press(Key.Text("2"), Key.Right, Key.Right, *type("값 한"), Key.Backspace, Key.Left, Key.Left)
        state.chosen(0) shouldBe 2
        state.draft(2) shouldBe "값 "
    }

    test("Ctrl-C 는 지금까지의 답을 남기고 중단한다") {
        val state = state()
        state.press(Key.Text("1"), Key.Interrupt) shouldBe Outcome.ABORT
        state.responses() shouldBe listOf("4", "", "")
    }

    test("아무 답도 없이 중단하면 빈 목록이다") {
        state().press(Key.Interrupt) shouldBe Outcome.ABORT
        state().responses() shouldBe emptyList()
    }

    test("검토 화면에서 숫자로 그 문항을 연다") {
        val state = state()
        state.press(Key.Tab, Key.Tab, Key.Tab, Key.Text("2"))
        state.mode shouldBe Mode.QUESTION
        state.current shouldBe 1
    }

    test("화면은 크기에 맞는 줄 수를 그리고 넘치지 않는다") {
        val state = QuizScreenState(
            listOf(choice("q1", 1, context = "긴 지문 ".repeat(200))),
            listOf(ChoiceOrder.identity(4)),
        )
        val frame = renderQuiz(state, ScreenSize(rows = 12, columns = 40), "track 01-stage · 단계 제목", "답함 0/1 · 0:00")
        frame.lines.size shouldBe 12
        frame.lines.forEach { (displayWidth(plain(it)) <= 40) shouldBe true }
    }

    test("커서가 가리키는 선지가 보이도록 내려 간다") {
        val state = QuizScreenState(
            listOf(choice("q1", 1, context = "긴 지문 ".repeat(200))),
            listOf(ChoiceOrder.of(listOf(3, 2, 1, 0))),
        )
        state.press(Key.Down, Key.Down)
        val frame = renderQuiz(state, ScreenSize(rows = 12, columns = 40), "t", "s")
        frame.lines.map(::plain).any { it.startsWith("❯ ③ 나 선지") } shouldBe true
        (frame.scroll >= 1) shouldBe true
    }

    test("PgUp 으로 지문을 올려 읽으면 커서를 따라 내려가지 않는다") {
        val state = QuizScreenState(
            listOf(choice("q1", 1, context = "긴 지문 ".repeat(200))),
            listOf(ChoiceOrder.identity(4)),
        )
        state.press(Key.Down, Key.Down, Key.Down)
        state.scroll = renderQuiz(state, ScreenSize(12, 40), "t", "s").scroll
        state.press(Key.PageUp, Key.Home)
        renderQuiz(state, ScreenSize(12, 40), "t", "s").scroll shouldBe 0
    }

    test("검토 화면은 학습자가 본 번호와 답하지 않은 문항을 보여 준다") {
        val state = state()
        state.press(Key.Text("1"), Key.Tab, Key.Tab, Key.Tab)
        val lines = renderQuiz(state, ScreenSize(20, 60), "t", "s").lines.map(::plain)
        lines shouldContain "  1. q1  ① 라 선지"
        lines.joinToString("\n") shouldContain "답하지 않은 문항이 2개 있습니다."
    }
})
