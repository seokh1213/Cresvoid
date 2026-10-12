private const val UNCLOSED_QUOTE = "\""
private const val NEAR_PREFIX = "NEAR/"
private const val MAX_NEAR_DISTANCE = 50

// 큰따옴표가 홀로 맞는 것은 닫는 큰따옴표가 없을 때뿐이다
private val LEXEME_PATTERN = Regex("""[()]|"[^"]*"|"|[^ ()"]+""")
private val NEAR_OPERATOR_FORMAT = Regex("NEAR/[1-9][0-9]?")

fun parseQuery(expression: String): QueryExpression? {
    val lexemes = lex(expression)?.filterNot(::isZeroTokenWordOrPhrase) ?: return null
    return QueryParser(lexemes).parse()
}

private sealed interface Lexeme {
    data object OpenParen : Lexeme
    data object CloseParen : Lexeme
    data object And : Lexeme
    data object Or : Lexeme
    data object Not : Lexeme
    data class Near(val maxDistance: Int) : Lexeme

    sealed interface WordOrPhrase : Lexeme {
        val tokens: List<String>
    }

    data class SearchWord(override val tokens: List<String>) : WordOrPhrase
    data class Phrase(override val tokens: List<String>) : WordOrPhrase
}

private fun lex(expression: String): List<Lexeme>? {
    val lexemes = mutableListOf<Lexeme>()
    for (match in LEXEME_PATTERN.findAll(expression)) {
        lexemes += lexemeOf(match.value) ?: return null
    }
    return lexemes
}

private fun lexemeOf(text: String): Lexeme? = when {
    text == "(" -> Lexeme.OpenParen
    text == ")" -> Lexeme.CloseParen
    text == "AND" -> Lexeme.And
    text == "OR" -> Lexeme.Or
    text == "NOT" -> Lexeme.Not
    text == UNCLOSED_QUOTE -> null
    text.startsWith('"') -> Lexeme.Phrase(tokenize(text.removeSurrounding("\"")))
    text.startsWith(NEAR_PREFIX) -> nearOperatorOf(text)
    else -> Lexeme.SearchWord(tokenize(text))
}

private fun nearOperatorOf(word: String): Lexeme.Near? {
    if (!NEAR_OPERATOR_FORMAT.matches(word)) return null
    val maxDistance = word.removePrefix(NEAR_PREFIX).toInt()
    return if (maxDistance <= MAX_NEAR_DISTANCE) Lexeme.Near(maxDistance) else null
}

private fun isZeroTokenWordOrPhrase(lexeme: Lexeme): Boolean =
    lexeme is Lexeme.WordOrPhrase && lexeme.tokens.isEmpty()

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
        if (!accept(Lexeme.Not)) return parseNear()
        val operand = parseNot() ?: return null
        return if (operand is QueryExpression.Not) operand.operand else QueryExpression.Not(operand)
    }

    private fun parseNear(): QueryExpression? {
        val left = proximityTermToken(peek())
        val near = peek(offset = 1) as? Lexeme.Near
        if (left == null || near == null) return parsePrimary()
        position += 2
        val right = proximityTermToken(peek()) ?: return null
        position++
        return QueryExpression.Near(left, right, near.maxDistance)
    }

    private fun parsePrimary(): QueryExpression? {
        if (accept(Lexeme.OpenParen)) {
            val inner = parseOr() ?: return null
            return if (accept(Lexeme.CloseParen)) inner else null
        }
        val operand = peek() as? Lexeme.WordOrPhrase ?: return null
        position++
        return if (operand is Lexeme.Phrase && operand.tokens.size > 1) {
            QueryExpression.Phrase(operand.tokens)
        } else {
            QueryExpression.Term(operand.tokens)
        }
    }

    private fun proximityTermToken(lexeme: Lexeme?): String? =
        (lexeme as? Lexeme.WordOrPhrase)?.tokens?.singleOrNull()

    private fun startsOperand(lexeme: Lexeme?): Boolean =
        lexeme == Lexeme.Not || lexeme == Lexeme.OpenParen || lexeme is Lexeme.WordOrPhrase

    private fun peek(offset: Int = 0): Lexeme? = lexemes.getOrNull(position + offset)

    private fun accept(expected: Lexeme): Boolean {
        if (peek() != expected) return false
        position++
        return true
    }
}
