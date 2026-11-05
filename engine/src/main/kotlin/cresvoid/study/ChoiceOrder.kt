package cresvoid.study

import cresvoid.content.Question
import cresvoid.content.QuestionType
import java.util.Random

/**
 * The order in which one question's choices are shown. Position i (0-based) shows original choice [original] at i.
 * Grading and progress keep the original numbers from quiz.json, so only presentation depends on this order.
 */
class ChoiceOrder private constructor(private val original: List<Int>) {
    val size: Int get() = original.size

    fun originalIndex(displayIndex: Int): Int = original[displayIndex]

    fun originalNumber(displayNumber: Int): Int = original[displayNumber - 1] + 1

    fun displayNumber(originalNumber: Int): Int = original.indexOf(originalNumber - 1) + 1

    /** Explanations point at choices with circled numerals, so they are rewritten to the numbers the learner saw. */
    fun remap(text: String): String = buildString(text.length) {
        for (char in text) {
            val number = CIRCLED.indexOf(char) + 1
            append(if (number in 1..size) CIRCLED[displayNumber(number) - 1] else char)
        }
    }

    override fun equals(other: Any?): Boolean = other is ChoiceOrder && other.original == original

    override fun hashCode(): Int = original.hashCode()

    override fun toString(): String = "ChoiceOrder$original"

    companion object {
        const val CIRCLED = "①②③④⑤⑥⑦⑧⑨⑩"

        fun identity(size: Int): ChoiceOrder = ChoiceOrder(List(size) { it })

        fun of(original: List<Int>): ChoiceOrder {
            require(original.sorted() == List(original.size) { it }) { "선지 순서가 순열이 아닙니다: $original" }
            return ChoiceOrder(original)
        }
    }
}

fun circled(number: Int): String = ChoiceOrder.CIRCLED.getOrNull(number - 1)?.toString() ?: "($number)"

fun Question.correctAnswerText(order: ChoiceOrder): String = when {
    type == QuestionType.CHOICE && answer in 1..choices.size -> "${order.displayNumber(answer)}번. ${choices[answer - 1]}"
    else -> answers.firstOrNull().orEmpty()
}

fun interface ChoiceShuffler {
    fun orderFor(question: Question): ChoiceOrder
}

object OriginalChoiceOrder : ChoiceShuffler {
    override fun orderFor(question: Question): ChoiceOrder = ChoiceOrder.identity(question.choices.size)
}

class RandomChoiceShuffler(private val random: Random) : ChoiceShuffler {
    override fun orderFor(question: Question): ChoiceOrder {
        if (question.type != QuestionType.CHOICE) return ChoiceOrder.identity(question.choices.size)
        return ChoiceOrder.of(MutableList(question.choices.size) { it }.apply { shuffle(random) })
    }
}
