package cresvoid.study

import cresvoid.content.Question
import cresvoid.content.QuestionType
import cresvoid.content.Quiz
import cresvoid.progress.QuizProgress
import cresvoid.progress.READING_SESSION_GAP
import cresvoid.progress.StageProgress
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val now: OffsetDateTime = OffsetDateTime.of(2026, 10, 8, 10, 0, 0, 0, ZoneOffset.ofHours(9))

private fun choice(id: String, answer: Int) = Question(
    id = id,
    type = QuestionType.CHOICE,
    prompt = "$id 발문",
    context = "",
    choices = listOf("가", "나", "다", "라", "마"),
    answer = answer,
    input = emptyList(),
    answers = emptyList(),
    refs = listOf("1"),
    quote = "핵심",
    explanation = "해설",
)

private val quiz = Quiz(listOf(choice("q1", 3), choice("q2", 2)))

private fun submit(before: QuizProgress, responses: List<String>, aborted: Boolean = false, timing: QuizTiming = QuizTiming.Measured(now.minusSeconds(95))): QuizGrade {
    val open = quiz.questions.filterNot { before.isClosed(it.id) }
    return grade(before, QuizSubmission(quiz, open, responses, aborted, timing), now)
}

class QuizGradingTest : FunSpec({
    test("첫 시도는 점수와 걸린 시간을 남긴다") {
        val result = submit(QuizProgress(), listOf("3", "1")) as QuizGrade.Graded
        withClue("정답 수") { result.correct shouldBe 1 }
        withClue("틀린 문항") { (result.wrong.map { it.id }) shouldBe listOf("q2") }
        withClue("기록") { result.progress shouldBe QuizProgress(resolved = listOf("q1"), wrong = mapOf("q2" to 1), firstCorrect = 1, firstTotal = 2, firstSeconds = 95) }
        withClue("아직 열린 문항이 있다") { !result.completed shouldBe true }
    }
    test("시간을 재지 않는 첫 시도는 걸린 시간을 남기지 않는다") {
        val result = submit(QuizProgress(), listOf("3", "2"), timing = QuizTiming.Unmeasured("읽기 기록이 없어")) as QuizGrade.Graded
        withClue("걸린 시간") { result.progress.firstSeconds shouldBe null }
        withClue("마친 시각") { result.progress.completedAt shouldBe now }
    }
    test("첫 시도를 그만두면 답하지 않은 문항을 틀린 것으로 센다") {
        val result = submit(QuizProgress(), listOf("3"), aborted = true) as QuizGrade.Graded
        withClue("첫 시도 점수") { result.progress.firstCorrect shouldBe 1 }
        withClue("오답 수") { result.progress.wrong shouldBe mapOf("q2" to 1) }
    }
    test("첫 시도에서 하나도 답하지 않고 그만두면 저장하지 않는다") {
        withClue("결과") { submit(QuizProgress(), emptyList(), aborted = true) shouldBe QuizGrade.NotSaved }
    }
    test("두 번째 시도부터 그만두면 저장하지 않는다") {
        val before = QuizProgress(wrong = mapOf("q1" to 1, "q2" to 1), firstCorrect = 0, firstTotal = 2)
        withClue("결과") { submit(before, listOf("3"), aborted = true) shouldBe QuizGrade.NotSaved }
    }
    test("세 번 틀리면 정답을 공개하고 문항을 닫는다") {
        val before = QuizProgress(resolved = listOf("q2"), wrong = mapOf("q1" to 2), firstCorrect = 0, firstTotal = 2, firstSeconds = 30)
        val result = submit(before, listOf("1")) as QuizGrade.Graded
        withClue("공개된 문항") { result.progress.revealed shouldBe listOf("q1") }
        withClue("오답 수") { result.progress.wrong["q1"] shouldBe 3 }
        withClue("첫 시도 점수는 그대로") { result.progress.firstCorrect shouldBe 0 }
        withClue("걸린 시간은 그대로") { result.progress.firstSeconds shouldBe 30 }
        withClue("모든 문항이 닫혔다") { result.completed shouldBe true }
    }
    test("short 답은 공백을 정규화하고 대소문자를 구분한다") {
        val question = choice("q", 1).copy(type = QuestionType.SHORT, choices = emptyList(), answer = 0, answers = listOf("1  2.5000"))
        withClue("공백 정규화") { question.isCorrect("  1 2.5000 ") shouldBe true }
        withClue("글자는 그대로 비교") { !question.isCorrect("1 2.5") shouldBe true }
        val word = question.copy(answers = listOf("Apple"))
        withClue("대소문자 구분") { !word.isCorrect("apple") shouldBe true }
    }
    test("choice 답은 선지 범위 안의 정수만 받는다") {
        val question = choice("q", 3)
        withClue("범위 안") { (question.accepts("5") && question.accepts("+1")) shouldBe true }
        withClue("범위 밖") { (!question.accepts("6") && !question.accepts("0") && !question.accepts("셋")) shouldBe true }
        withClue("앞뒤 공백과 앞자리 0") { question.isCorrect(" 03 ") shouldBe true }
    }
    test("시간 측정은 읽은 기록과 마지막으로 읽은 시각을 따른다") {
        val opened = OffsetDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneOffset.UTC)
        val lastRead = opened.plusMinutes(20)
        val stage = StageProgress(openedAt = opened, lastReadAt = lastRead)
        withClue("한 시간 안") { quizTiming(stage, lastRead.plus(READING_SESSION_GAP)) shouldBe QuizTiming.Measured(opened) }
        withClue("한 시간이 지남") { (quizTiming(stage, lastRead.plus(READING_SESSION_GAP).plusSeconds(1)) is QuizTiming.Unmeasured) shouldBe true }
        withClue("읽은 기록 없음") { (quizTiming(StageProgress(), opened) is QuizTiming.Unmeasured) shouldBe true }
    }
})
