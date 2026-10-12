fun parseQuery(expression: String): QueryExpression? {
    val lexemes = lex(expression).filterNot(::isZeroTokenWord)
    return QueryParser(lexemes).parse()
}

private sealed interface Lexeme {
    data object OpenParen : Lexeme
    data object CloseParen : Lexeme
    data object And : Lexeme
    data object Or : Lexeme
    data object Not : Lexeme
    data class SearchWord(val tokens: List<String>) : Lexeme
}

private val LEXEME_PATTERN = Regex("[()]|[^ ()]+")

private fun lex(expression: String): List<Lexeme> =
    LEXEME_PATTERN.findAll(expression).map { lexemeOf(it.value) }.toList()

private fun lexemeOf(text: String): Lexeme = when (text) {
    "(" -> Lexeme.OpenParen
    ")" -> Lexeme.CloseParen
    "AND" -> Lexeme.And
    "OR" -> Lexeme.Or
    "NOT" -> Lexeme.Not
    else -> Lexeme.SearchWord(tokenize(text))
}

private fun isZeroTokenWord(lexeme: Lexeme): Boolean =
    lexeme is Lexeme.SearchWord && lexeme.tokens.isEmpty()

private class QueryParser(private val lexemes: List<Lexeme>) {
    private var position = 0

    fun parse(): QueryExpression? {
        val expression = parseOr() ?: return null
        return if (position == lexemes.size) expression else null
    }

    private fun parseOr(): QueryExpression? {
        val operands = mutableListOf(parseAnd() ?: return null)
        while (accept(Lexeme.Or)) {
            operands += parseAnd() ?: return null
        }
        return operands.singleOrNull() ?: QueryExpression.Or(operands)
    }

    private fun parseAnd(): QueryExpression? {
        val operands = mutableListOf(parseNot() ?: return null)
        while (accept(Lexeme.And) || startsOperand(peek())) {
            operands += parseNot() ?: return null
        }
        return operands.singleOrNull() ?: QueryExpression.And(operands)
    }

    private fun parseNot(): QueryExpression? {
        if (!accept(Lexeme.Not)) return parsePrimary()
        val operand = parseNot() ?: return null
        return if (operand is QueryExpression.Not) operand.operand else QueryExpression.Not(operand)
    }

    private fun parsePrimary(): QueryExpression? {
        if (accept(Lexeme.OpenParen)) {
            val inner = parseOr() ?: return null
            return if (accept(Lexeme.CloseParen)) inner else null
        }
        val word = peek() as? Lexeme.SearchWord ?: return null
        position++
        return QueryExpression.Term(word.tokens)
    }

    private fun startsOperand(lexeme: Lexeme?): Boolean =
        lexeme == Lexeme.Not || lexeme == Lexeme.OpenParen || lexeme is Lexeme.SearchWord

    private fun peek(): Lexeme? = lexemes.getOrNull(position)

    private fun accept(expected: Lexeme): Boolean {
        if (peek() != expected) return false
        position++
        return true
    }
}
