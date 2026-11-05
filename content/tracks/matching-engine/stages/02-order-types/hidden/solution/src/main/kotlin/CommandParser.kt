object CommandParser {
    private val integer = Regex("-?[0-9]+")
    private val identifier = Regex("[A-Za-z][A-Za-z0-9_]{0,11}")
    private val separator = Regex("[ \\t]+")

    fun tokens(line: String): List<String> =
        line.trim(' ', '\t').takeIf { it.isNotEmpty() }?.split(separator).orEmpty()

    fun parse(tokens: List<String>): Command = when (tokens.firstOrNull()) {
        "LIMIT", "IOC", "FOK", "MARKET" -> submit(tokens)
        "CANCEL" -> {
            shape(tokens, 2)
            Command.Cancel(id(tokens[1]))
        }
        "AMEND" -> {
            shape(tokens, 4, listOf(2, 3))
            Command.Amend(id(tokens[1]), number(tokens[2], 1), number(tokens[3], 0))
        }
        "BOOK" -> { shape(tokens, 1); Command.Book }
        else -> throw Rejected("FORMAT")
    }

    private fun submit(tokens: List<String>): Command.Submit {
        val kind = OrderKind.valueOf(tokens[0])
        val hasPrice = kind != OrderKind.MARKET
        shape(tokens, if (hasPrice) 6 else 5, if (hasPrice) listOf(4, 5) else listOf(4))
        val id = id(tokens[1])
        val owner = id(tokens[2])
        val side = when (tokens[3]) {
            "BUY" -> Side.BUY
            "SELL" -> Side.SELL
            else -> throw Rejected("VALUE")
        }
        val price = if (hasPrice) number(tokens[4], 1) else null
        val quantity = number(tokens.last(), 1)
        return Command.Submit(OrderRequest(id, owner, side, kind, price, quantity))
    }

    private fun shape(tokens: List<String>, count: Int, numeric: List<Int> = emptyList()) {
        if (tokens.size != count || numeric.any { !integer.matches(tokens[it]) }) {
            throw Rejected("FORMAT")
        }
    }

    private fun id(token: String): String {
        if (!identifier.matches(token)) throw Rejected("VALUE")
        return token
    }

    private fun number(token: String, minimum: Long): Long {
        val value = token.toLongOrNull() ?: throw Rejected("VALUE")
        if (value !in minimum..1_000_000_000L) throw Rejected("VALUE")
        return value
    }
}
