class QueryParser(private val tokens: List<QueryToken>) {
    private var position = 0

    fun parse(): SearchQuery {
        val query = parseOr()
        if (current().kind != TokenKind.END) fail("trailing")
        return query
    }

    private fun parseOr(): SearchQuery {
        var query = parseAnd()
        while (accept(TokenKind.OR)) {
            query = SearchQuery.Or(query, parseAnd())
        }
        return query
    }

    private fun parseAnd(): SearchQuery {
        var query = parseUnary()
        while (true) {
            query = when {
                accept(TokenKind.AND) -> SearchQuery.And(query, parseUnary())
                startsOperand(current().kind) -> SearchQuery.And(query, parseUnary())
                else -> return query
            }
        }
    }

    private fun parseUnary(): SearchQuery =
        if (accept(TokenKind.NOT)) SearchQuery.Not(parseUnary()) else parsePrimary()

    private fun parsePrimary(): SearchQuery {
        val token = current()
        return when (token.kind) {
            TokenKind.WORD -> {
                position++
                SearchQuery.Term(token.text.lowercase())
            }
            TokenKind.PHRASE -> {
                position++
                val words = token.text.split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .map { it.lowercase() }
                if (words.isEmpty()) {
                    throw QuerySyntaxException("empty-phrase", token.offset)
                }
                SearchQuery.Phrase(words)
            }
            TokenKind.PRICE -> {
                position++
                parsePrice(token)
            }
            TokenKind.LEFT -> {
                position++
                val nested = parseOr()
                if (!accept(TokenKind.RIGHT)) fail("closing-parenthesis")
                nested
            }
            else -> fail("operand")
        }
    }

    private fun parsePrice(token: QueryToken): SearchQuery.Price {
        val range = token.text.substring("price:".length)
        val separator = range.indexOf("..")
        if (separator == -1) {
            throw QuerySyntaxException("price-range", token.offset)
        }
        val lowerText = range.substring(0, separator)
        val upperText = range.substring(separator + 2)
        val lower = priceBound(lowerText, token.offset)
        val upper = priceBound(upperText, token.offset)
        if (lower == null && upper == null) {
            throw QuerySyntaxException("price-range", token.offset)
        }
        if (lower != null && upper != null && lower > upper) {
            throw QuerySyntaxException("price-order", token.offset)
        }
        return SearchQuery.Price(lower, upper)
    }

    private fun priceBound(text: String, offset: Int): Long? {
        if (text.isEmpty()) return null
        if (text.any { it !in '0'..'9' }) {
            throw QuerySyntaxException("price-number", offset)
        }
        return text.toLongOrNull() ?: throw QuerySyntaxException("price-number", offset)
    }

    private fun startsOperand(kind: TokenKind): Boolean = when (kind) {
        TokenKind.WORD, TokenKind.PHRASE, TokenKind.PRICE, TokenKind.LEFT, TokenKind.NOT -> true
        else -> false
    }

    private fun current(): QueryToken = tokens[position]

    private fun accept(kind: TokenKind): Boolean {
        if (current().kind != kind) return false
        position++
        return true
    }

    private fun fail(reason: String): Nothing = throw QuerySyntaxException(reason, current().offset)
}
