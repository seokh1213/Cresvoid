private val SYMBOLS = listOf("<>", "<=", ">=", "(", ")", ",", "*", "=", "<", ">")

fun tokenize(line: String): List<Token>? {
    val tokens = mutableListOf<Token>()
    var position = 0
    while (position < line.length) {
        val start = position
        val char = line[start]
        when {
            char == ' ' -> position++
            char.isWordStart() -> {
                position = line.skipWhile(start + 1) { it.isWordPart() }
                tokens += Token.Word(line.substring(start, position))
            }
            char.isDecimalDigit() || (char == '-' && line.getOrNull(start + 1)?.isDecimalDigit() == true) -> {
                position = line.skipWhile(start + 1) { it.isDecimalDigit() }
                tokens += Token.Integer(line.substring(start, position))
            }
            char == '\'' -> {
                val closing = line.indexOf('\'', start + 1)
                if (closing < 0) return null
                val text = line.substring(start + 1, closing)
                if (!text.all(::isStringCharacter)) return null
                tokens += Token.Text(text)
                position = closing + 1
            }
            else -> {
                val symbol = SYMBOLS.firstOrNull { line.startsWith(it, start) } ?: return null
                tokens += Token.Symbol(symbol)
                position += symbol.length
            }
        }
    }
    return tokens
}

fun isStringCharacter(char: Char): Boolean =
    (char in ' '..'~' && char != '\'' && char != '\\') || char in '가'..'힣'

private fun Char.isWordStart(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this == '_'

private fun Char.isWordPart(): Boolean = isWordStart() || isDecimalDigit()

private fun Char.isDecimalDigit(): Boolean = this in '0'..'9'

private fun String.skipWhile(from: Int, predicate: (Char) -> Boolean): Int {
    var position = from
    while (position < length && predicate(this[position])) position++
    return position
}
