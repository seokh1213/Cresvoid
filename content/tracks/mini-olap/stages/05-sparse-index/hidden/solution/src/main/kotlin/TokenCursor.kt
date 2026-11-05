private val NAME = Regex("[a-z][a-z0-9_]{0,31}")

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
        return intOf(token)
    }

    fun value(): Value? {
        val token = tokens.getOrNull(position) ?: syntaxError()
        position++
        return when {
            token is Token.Integer -> IntValue(intOf(token))
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

    private fun intOf(token: Token.Integer): Long {
        val number = parseInt(token.text)
        if (number == NOT_AN_INT) syntaxError()
        return number
    }
}
