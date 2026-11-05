package cresvoid.study

import cresvoid.content.Question
import cresvoid.content.QuestionType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Random

private fun question(type: QuestionType, choices: List<String>, answer: Int = 1) = Question(
    id = "q1",
    type = type,
    prompt = "발문",
    context = "",
    choices = choices,
    answer = answer,
    input = emptyList(),
    answers = listOf("42"),
    refs = listOf("1"),
    quote = "핵심",
    explanation = "해설",
)

class ChoiceOrderTest : FunSpec({
    test("화면 번호와 원래 번호를 서로 바꾼다") {
        val order = ChoiceOrder.of(listOf(2, 0, 4, 1, 3))
        order.originalNumber(1) shouldBe 3
        order.originalNumber(3) shouldBe 5
        (1..5).map { order.displayNumber(order.originalNumber(it)) } shouldBe (1..5).toList()
    }

    test("해설의 원문자를 학습자가 본 번호로 바꾼다") {
        val order = ChoiceOrder.of(listOf(2, 0, 4, 1, 3))
        order.remap("정답은 ③이다. ① 틀림. ⑤ 맞음. [3] 문단") shouldBe "정답은 ①이다. ② 틀림. ③ 맞음. [3] 문단"
    }

    test("선지 수보다 큰 원문자는 그대로 둔다") {
        ChoiceOrder.of(listOf(1, 0)).remap("① ② ③") shouldBe "② ① ③"
    }

    test("정답 문구는 화면 번호로 보여 준다") {
        val q = question(QuestionType.CHOICE, listOf("가", "나", "다"), answer = 3)
        q.correctAnswerText(ChoiceOrder.of(listOf(2, 0, 1))) shouldBe "1번. 다"
        question(QuestionType.SHORT, emptyList()).correctAnswerText(ChoiceOrder.identity(0)) shouldBe "42"
    }

    test("순열이 아닌 순서는 거절한다") {
        shouldThrow<IllegalArgumentException> { ChoiceOrder.of(listOf(0, 0, 1)) }
    }

    test("무작위 순서는 같은 시드에서 같고 모든 선지를 한 번씩 담는다") {
        val q = question(QuestionType.CHOICE, listOf("가", "나", "다", "라", "마"))
        val first = RandomChoiceShuffler(Random(7)).orderFor(q)
        first shouldBe RandomChoiceShuffler(Random(7)).orderFor(q)
        (0 until 5).map(first::originalIndex).sorted() shouldBe (0 until 5).toList()
        val orders = (1L..20L).map { RandomChoiceShuffler(Random(it)).orderFor(q) }.toSet()
        orders.size shouldNotBe 1
    }

    test("단답형은 섞지 않는다") {
        RandomChoiceShuffler(Random(1)).orderFor(question(QuestionType.SHORT, emptyList())) shouldBe ChoiceOrder.identity(0)
        OriginalChoiceOrder.orderFor(question(QuestionType.CHOICE, listOf("가", "나"))) shouldBe ChoiceOrder.identity(2)
    }
})
