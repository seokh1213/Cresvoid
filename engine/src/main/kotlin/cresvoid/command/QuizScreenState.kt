package cresvoid.command

import cresvoid.content.Question
import cresvoid.content.QuestionType
import cresvoid.study.ChoiceOrder
import cresvoid.terminal.Key

/** What the full-screen quiz holds between keys. Choices are tracked in display numbers and mapped back on submit. */
class QuizScreenState(
    val questions: List<Question>,
    val orders: List<ChoiceOrder>,
) {
    enum class Mode { QUESTION, REVIEW }

    enum class Outcome { CONTINUE, SUBMIT, ABORT }

    var current = 0
        private set
    var mode = Mode.QUESTION
        private set

    /** Body scroll offset. The view clamps it and writes back the value it drew. */
    var scroll = 0

    /** Whether the view should scroll to the choice under the cursor. Paging turns this off so a long passage can be read. */
    var followCursor = true
        private set

    private val cursors = IntArray(questions.size)
    private val chosen = arrayOfNulls<Int>(questions.size)
    private val drafts = Array(questions.size) { "" }

    init {
        require(orders.size == questions.size) { "문항 수와 선지 순서 수가 다릅니다" }
    }

    fun cursor(index: Int): Int = cursors[index]

    fun chosen(index: Int): Int? = chosen[index]

    fun draft(index: Int): String = drafts[index]

    fun isAnswered(index: Int): Boolean =
        if (questions[index].type == QuestionType.CHOICE) chosen[index] != null else drafts[index].isNotBlank()

    fun answeredCount(): Int = questions.indices.count(::isAnswered)

    fun firstUnanswered(): Int? = questions.indices.firstOrNull { !isAnswered(it) }

    fun handle(key: Key): Outcome {
        if (key == Key.Interrupt || key == Key.EndOfInput) return Outcome.ABORT
        return when (mode) {
            Mode.QUESTION -> onQuestion(key)
            Mode.REVIEW -> onReview(key)
        }
    }

    /** Answers in quiz.json numbering, aligned with [questions]. Unanswered questions are empty strings. */
    fun responses(): List<String> {
        val responses = questions.indices.map { index ->
            if (questions[index].type == QuestionType.CHOICE) {
                chosen[index]?.let { orders[index].originalNumber(it).toString() }.orEmpty()
            } else {
                drafts[index].trim()
            }
        }
        return if (responses.all { it.isEmpty() }) emptyList() else responses
    }

    private fun onQuestion(key: Key): Outcome {
        when (key) {
            Key.Left, Key.BackTab -> move(current - 1)
            Key.Right, Key.Tab -> advance()
            Key.PageUp -> page(-PAGE_LINES)
            Key.PageDown -> page(PAGE_LINES)
            Key.Home -> page(Int.MIN_VALUE / 2)
            else -> if (questions[current].type == QuestionType.CHOICE) onChoice(key) else onShort(key)
        }
        return Outcome.CONTINUE
    }

    private fun onChoice(key: Key) {
        val size = questions[current].choices.size
        when (key) {
            Key.Up -> pointAt(cursors[current] - 1)
            Key.Down -> pointAt(cursors[current] + 1)
            Key.Enter -> {
                chosen[current] = cursors[current] + 1
                advance()
            }
            is Key.Text -> key.value.toIntOrNull()?.takeIf { it in 1..size }?.let { number ->
                pointAt(number - 1)
                chosen[current] = number
            }
            else -> Unit
        }
    }

    private fun onShort(key: Key) {
        when (key) {
            is Key.Text -> drafts[current] += key.value
            Key.Backspace -> drafts[current] = drafts[current].dropLastCodePoint()
            Key.Enter -> if (drafts[current].isNotBlank()) advance()
            Key.Up -> page(-1)
            Key.Down -> page(1)
            else -> Unit
        }
    }

    private fun onReview(key: Key): Outcome {
        when (key) {
            Key.Enter -> {
                val missing = firstUnanswered() ?: return Outcome.SUBMIT
                openQuestion(missing)
            }
            Key.Left, Key.BackTab, Key.Escape -> openQuestion(questions.lastIndex)
            is Key.Text -> key.value.toIntOrNull()?.takeIf { it in 1..questions.size }?.let { openQuestion(it - 1) }
            else -> Unit
        }
        return Outcome.CONTINUE
    }

    private fun pointAt(index: Int) {
        cursors[current] = index.coerceIn(0, questions[current].choices.lastIndex.coerceAtLeast(0))
        followCursor = true
    }

    private fun page(lines: Int) {
        scroll += lines
        followCursor = false
    }

    private fun advance() {
        if (current == questions.lastIndex) {
            mode = Mode.REVIEW
            scroll = 0
        } else {
            move(current + 1)
        }
    }

    private fun openQuestion(index: Int) {
        mode = Mode.QUESTION
        move(index)
    }

    private fun move(index: Int) {
        current = index.coerceIn(0, questions.lastIndex)
        scroll = 0
        followCursor = true
    }

    private companion object {
        const val PAGE_LINES = 10
    }
}

private fun String.dropLastCodePoint(): String {
    if (isEmpty()) return this
    val end = if (length >= 2 && this[length - 1].isLowSurrogate() && this[length - 2].isHighSurrogate()) length - 2 else length - 1
    return substring(0, end)
}
