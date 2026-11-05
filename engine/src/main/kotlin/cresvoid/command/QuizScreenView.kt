package cresvoid.command

import cresvoid.command.QuizScreenState.Mode
import cresvoid.content.Question
import cresvoid.content.QuestionType
import cresvoid.study.circled
import cresvoid.terminal.ScreenSize
import cresvoid.terminal.bold
import cresvoid.terminal.dim
import cresvoid.terminal.displayWidth
import cresvoid.terminal.green
import cresvoid.terminal.inverse
import cresvoid.terminal.truncateDisplay
import cresvoid.terminal.wrapDisplay
import cresvoid.terminal.yellow

class QuizFrame(val lines: List<String>, val scroll: Int)

private class Body(val lines: List<String>, val focus: IntRange?)

private const val CHROME_ROWS = 5

/** Draws one frame: header, question tabs, scrolling body, and a key guide that matches the current question. */
fun renderQuiz(state: QuizScreenState, size: ScreenSize, title: String, status: String): QuizFrame {
    val width = size.columns.coerceAtLeast(20)
    val bodyRows = (size.rows - CHROME_ROWS).coerceAtLeast(1)
    val body = if (state.mode == Mode.REVIEW) reviewBody(state, width) else questionBody(state, width)
    val scroll = clampScroll(state, body, bodyRows)
    val visible = body.lines.drop(scroll).take(bodyRows)
    val rule = "─".repeat(width)
    val lines = buildList {
        add(header(title, status, width))
        add(tabs(state, width))
        add(dim(if (scroll > 0) marked(rule, " ↑ 위에 더 있음 ") else rule))
        addAll(visible)
        repeat(bodyRows - visible.size) { add("") }
        add(dim(if (scroll + bodyRows < body.lines.size) marked(rule, " ↓ 아래에 더 있음 (PgDn) ") else rule))
        add(dim(truncateDisplay(guide(state), width)))
    }
    return QuizFrame(lines, scroll)
}

private fun clampScroll(state: QuizScreenState, body: Body, rows: Int): Int {
    var scroll = state.scroll
    val focus = body.focus
    if (state.followCursor && focus != null) {
        if (focus.first < scroll) scroll = focus.first
        if (focus.last >= scroll + rows) scroll = focus.last - rows + 1
    }
    return scroll.coerceIn(0, (body.lines.size - rows).coerceAtLeast(0))
}

private fun header(title: String, status: String, width: Int): String {
    val right = " $status "
    val left = truncateDisplay(" $title", (width - displayWidth(right)).coerceAtLeast(1))
    val gap = (width - displayWidth(left) - displayWidth(right)).coerceAtLeast(0)
    return inverse(left + " ".repeat(gap) + right)
}

private fun tabs(state: QuizScreenState, width: Int): String {
    val parts = state.questions.indices.map { index ->
        val label = " ${index + 1}${if (state.isAnswered(index)) "●" else "○"} "
        if (state.mode == Mode.QUESTION && index == state.current) inverse(label) else label
    }
    val review = if (state.mode == Mode.REVIEW) inverse(" 제출 ") else dim(" 제출 ")
    val plain = state.questions.indices.joinToString("") { " ${it + 1}● " } + " 제출 "
    return if (displayWidth(plain) <= width) parts.joinToString("") + review else "${state.current + 1}/${state.questions.size}"
}

private fun questionBody(state: QuizScreenState, width: Int): Body {
    val index = state.current
    val question = state.questions[index]
    val lines = mutableListOf<String>()
    wrapDisplay("${question.id}. ${question.prompt}", width).forEach { lines += bold(it) }
    if (question.context.isNotEmpty()) {
        lines += ""
        lines += hanging(question.context, width, "  ", "  ")
    }
    if (question.input.isNotEmpty()) {
        lines += ""
        lines += "  입력:"
        question.input.forEach { lines += truncateDisplay("    $it", width) }
    }
    lines += ""
    var focus: IntRange? = null
    if (question.type == QuestionType.CHOICE) {
        val order = state.orders[index]
        for (display in 0 until order.size) {
            val pointed = display == state.cursor(index)
            val picked = state.chosen(index) == display + 1
            val prefix = "${if (pointed) "❯" else " "} ${circled(display + 1)} "
            val text = question.choices[order.originalIndex(display)] + if (picked) "  ✓" else ""
            val start = lines.size
            hanging(text, width, prefix, " ".repeat(displayWidth(prefix))).forEach { line ->
                lines += when {
                    picked -> green(bold(line))
                    pointed -> bold(line)
                    else -> line
                }
            }
            if (pointed) focus = start until lines.size
        }
    } else {
        val start = lines.size
        lines += hanging("답: ${state.draft(index)}▏", width, "", "    ")
        focus = start until lines.size
    }
    return Body(lines, focus)
}

private fun reviewBody(state: QuizScreenState, width: Int): Body {
    val lines = mutableListOf(bold("제출 전에 답을 확인하세요."), "")
    state.questions.forEachIndexed { index, question ->
        val answer = answerSummary(state, index, question)
        lines += truncateDisplay("  ${index + 1}. ${question.id}  $answer", width)
    }
    lines += ""
    val missing = state.questions.size - state.answeredCount()
    lines += if (missing == 0) "Enter 를 누르면 모든 답을 한꺼번에 채점합니다." else yellow("답하지 않은 문항이 ${missing}개 있습니다.")
    return Body(lines, null)
}

private fun answerSummary(state: QuizScreenState, index: Int, question: Question): String {
    if (!state.isAnswered(index)) return yellow("답하지 않음")
    if (question.type != QuestionType.CHOICE) return state.draft(index).trim()
    val picked = state.chosen(index) ?: return yellow("답하지 않음")
    return "${circled(picked)} ${question.choices[state.orders[index].originalIndex(picked - 1)]}"
}

private fun guide(state: QuizScreenState): String = when {
    state.mode == Mode.REVIEW && state.firstUnanswered() == null -> "Enter 제출 · 숫자 그 문항으로 · ← 돌아가기 · Ctrl-C 중단"
    state.mode == Mode.REVIEW -> "Enter 답하지 않은 문항으로 · 숫자 그 문항으로 · ← 돌아가기 · Ctrl-C 중단"
    state.questions[state.current].type == QuestionType.CHOICE ->
        "↑↓·숫자 고르기 · Enter 다음 · ←→ 문항 이동 · PgUp/PgDn 스크롤 · Ctrl-C 중단"
    else -> "답 입력 · Enter 다음 · ←→ 문항 이동 · PgUp/PgDn 스크롤 · Ctrl-C 중단"
}

private fun hanging(text: String, width: Int, first: String, rest: String): List<String> {
    val available = (width - displayWidth(first)).coerceAtLeast(1)
    return wrapDisplay(text, available).mapIndexed { index, line -> (if (index == 0) first else rest) + line }
}

private fun marked(rule: String, label: String): String =
    rule.take(2) + label + rule.drop(2 + displayWidth(label))
