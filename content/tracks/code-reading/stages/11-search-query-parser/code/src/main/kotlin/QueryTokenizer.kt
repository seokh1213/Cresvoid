enum class TokenKind {
    WORD,
    PHRASE,
    PRICE,
    AND,
    OR,
    NOT,
    LEFT,
    RIGHT,
    END,
}

data class QueryToken(
    val kind: TokenKind,
    val text: String,
    val offset: Int,
)

class QuerySyntaxException(
    val reason: String,
    val offset: Int,
) : RuntimeException(reason)

class QueryTokenizer(private val source: String) {
    private var offset = 0

    fun tokenize(): List<QueryToken> {
        val tokens = mutableListOf<QueryToken>()
        while (offset < source.length) {
            val character = source[offset]
            when {
                character.isWhitespace() -> offset++
                character == '(' -> tokens.add(punctuation(TokenKind.LEFT))
                character == ')' -> tokens.add(punctuation(TokenKind.RIGHT))
                character == '"' -> tokens.add(phrase())
                character.isLetterOrDigit() -> tokens.add(word())
                else -> throw QuerySyntaxException("character", offset)
            }
        }
        tokens.add(
            QueryToken(TokenKind.END, "", source.length),
        )
        return tokens.toList()
    }

    private fun punctuation(kind: TokenKind): QueryToken {
        val token = QueryToken(
            kind,
            source[offset].toString(),
            offset,
        )
        offset++
        return token
    }

    private fun phrase(): QueryToken {
        val start = offset
        val closingQuote = source.indexOf('"', start + 1)
        // Search-box previews accept a phrase while its closing quote is still being typed.
        val end = if (closingQuote == -1) source.length else closingQuote
        val text = source.substring(start + 1, end)
        offset = if (closingQuote == -1) end else end + 1
        return QueryToken(TokenKind.PHRASE, text, start)
    }

    private fun word(): QueryToken {
        val start = offset
        while (offset < source.length && isWordCharacter(source[offset])) {
            offset++
        }
        val text = source.substring(start, offset)
        val kind = when (text) {
            "AND" -> TokenKind.AND
            "OR" -> TokenKind.OR
            "NOT" -> TokenKind.NOT
            else -> if (text.startsWith("price:")) TokenKind.PRICE else TokenKind.WORD
        }
        return QueryToken(kind, text, start)
    }

    private fun isWordCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character in "_-.:"
}
