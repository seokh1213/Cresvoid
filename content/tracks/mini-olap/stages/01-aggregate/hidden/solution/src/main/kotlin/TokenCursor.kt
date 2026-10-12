private val NAME = Regex("[a-z][a-z0-9_]{0,31}")
private val INTEGER = Regex("0|-?[1-9][0-9]*")
private val INT_RANGE = -1_000_000_000L..1_000_000_000L

fun parseIntValue(text: String): Long? {
    if (!INTEGER.matches(text)) return null
    return text.toLongOrNull()?.takeIf { it in INT_RANGE }
}

class TokenCursor(private val tokens: List<Token>) {
    private var position = 0

    fun nextWord(): String? = (tokens.getOrNull(position) as? Token.Word)?.text

    fun acceptKeyword(keyword: String): Boolean {
        if (nextWord() != keyword) return false
        position++
        return true
    }

    fun expectKeyword(keyword: String) {
        if (!acceptKeyword(keyword)) syntaxError()
    }

    fun acceptSymbol(symbol: String): Boolean {
        if ((tokens.getOrNull(position) as? Token.Symbol)?.text != symbol) return false
        position++
        return true
    }

    fun expectSymbol(symbol: String) {
        if (!acceptSymbol(symbol)) syntaxError()
    }

    fun skip() {
        position++
    }

    fun name(): String {
        val name = nextWord()?.takeIf { NAME.matches(it) } ?: syntaxError()
        position++
        return name
    }

    fun integer(): Long {
        val token = tokens.getOrNull(position) as? Token.Integer ?: syntaxError()
        position++
        return parseIntValue(token.text) ?: syntaxError()
    }

    fun value(): Value? {
        val token = tokens.getOrNull(position) ?: syntaxError()
        position++
        return when {
            token is Token.Integer -> IntValue(parseIntValue(token.text) ?: syntaxError())
            token is Token.Text -> StringValue(token.value)
            token is Token.Word && token.text == "NULL" -> null
            else -> syntaxError()
        }
    }

    fun comparisonOperator(): ComparisonOperator {
        val symbol = (tokens.getOrNull(position) as? Token.Symbol)?.text
        val operator = ComparisonOperator.entries.firstOrNull { it.symbol == symbol } ?: syntaxError()
        position++
        return operator
    }

    fun <T> commaSeparated(element: () -> T): List<T> {
        val elements = mutableListOf(element())
        while (acceptSymbol(",")) elements += element()
        return elements
    }

    fun expectEnd() {
        if (position != tokens.size) syntaxError()
    }
}
