package cresvoid.terminal

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

fun quoted(text: String): String = buildString {
    append('"')
    for (char in text) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\t' -> append("\\t")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char.isISOControl() || Character.getType(char) == Character.FORMAT.toInt() || (char.isWhitespace() && char != ' ') ->
                append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

private fun Int.displayColumns(): Int {
    val wide = this in 0x1100..0x115F ||
        this in 0x2E80..0xA4CF ||
        this in 0xAC00..0xD7A3 ||
        this in 0xF900..0xFAFF ||
        this in 0xFF00..0xFF60
    return if (wide) 2 else 1
}

fun displayWidth(text: String): Int = text.codePoints().map { it.displayColumns() }.sum()

fun padRight(text: String, width: Int): String {
    val gap = width - displayWidth(text)
    return if (gap <= 0) text else text + " ".repeat(gap)
}

fun truncateDisplay(text: String, limit: Int): String {
    if (displayWidth(text) <= limit) return text
    val kept = StringBuilder()
    var width = 0
    for (codePoint in text.codePoints()) {
        val columns = codePoint.displayColumns()
        if (width + columns > limit - 1) break
        kept.appendCodePoint(codePoint)
        width += columns
    }
    return kept.append('…').toString()
}

fun formatSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes == 0) "${rest}초" else "${minutes}분 ${rest}초"
}

fun formatElapsed(duration: Duration): String {
    val seconds = BigDecimal.valueOf(duration.toNanos(), 9)
    return seconds.setScale(1, RoundingMode.HALF_EVEN).toPlainString() + "초"
}

fun indent(text: String, prefix: String): String =
    text.trimEnd('\n').split("\n").joinToString("\n") { prefix + it }

fun formatRefs(refs: List<String>): String = refs.joinToString(" ") { "[$it]" }
