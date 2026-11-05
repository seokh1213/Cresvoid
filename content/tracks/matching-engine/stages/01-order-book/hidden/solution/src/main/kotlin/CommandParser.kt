object CommandParser {
    private val integer = Regex("-?[0-9]+")
    private val identifier = Regex("[A-Za-z][A-Za-z0-9_]{0,11}")
    private val separator = Regex("[ \\t]+")

    fun tokens(line: String): List<String> =
        line.trim(' ', '\t').takeIf { it.isNotEmpty() }?.split(separator).orEmpty()

    fun parse(tokens: List<String>): Command = when (tokens.firstOrNull()) {
        "LIMIT" -> submit(tokens)
        "CANCEL" -> {
            shape(tokens, 2)
            Command.Cancel(id(tokens[1]))
        }
        "BOOK" -> { shape(tokens, 1); Command.Book }
        else -> throw Rejected("FORMAT")
    }

    private fun submit(tokens: List<String>): Command.Submit {
        shape(tokens, 6, listOf(4, 5))
        val id = id(tokens[1])
        val owner = id(tokens[2])
        val side = when (tokens[3]) {
            "BUY" -> Side.BUY
            "SELL" -> Side.SELL
            else -> throw Rejected("VALUE")
        }
        val price = number(tokens[4], 1)
        val quantity = number(tokens.last(), 1)
        return Command.Submit(OrderRequest(id, owner, side, price, quantity))
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
