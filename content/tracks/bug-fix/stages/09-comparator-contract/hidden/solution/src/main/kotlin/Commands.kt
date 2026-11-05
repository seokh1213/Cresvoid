sealed interface CatalogCommand {
    data class Put(val product: Product) : CatalogCommand
    data class Score(val id: String, val score: Double) : CatalogCommand
    data class Stock(val id: String, val stock: Int) : CatalogCommand
    data class Remove(val id: String) : CatalogCommand
    data class Get(val id: String) : CatalogCommand
    data class Show(val category: String, val limit: Int) : CatalogCommand
    data class Policy(val category: String, val policy: RecommendationPolicy) : CatalogCommand
}

class CommandParser {
    fun parse(line: String): CatalogCommand {
        val tokens = line.trim().split(Regex("\\s+"))
        return when (tokens.first()) {
            "PUT" -> parseProduct(tokens)
            "POLICY" -> parsePolicy(tokens)
            "SCORE" -> {
                require(tokens.size == 3)
                CatalogCommand.Score(tokens[1], parseScore(tokens[2]))
            }
            "STOCK" -> {
                require(tokens.size == 3)
                CatalogCommand.Stock(tokens[1], parseStock(tokens[2]))
            }
            "REMOVE" -> {
                require(tokens.size == 2)
                CatalogCommand.Remove(tokens[1])
            }
            "GET" -> {
                require(tokens.size == 2)
                CatalogCommand.Get(tokens[1])
            }
            "SHOW" -> {
                require(tokens.size == 3)
                val limit = tokens[2].toInt()
                require(limit in 1..10000)
                CatalogCommand.Show(tokens[1], limit)
            }
            else -> error("Unknown catalog command")
        }
    }

    private fun parseProduct(tokens: List<String>): CatalogCommand.Put {
        require(tokens.size == 5)
        val product = Product(
            id = tokens[1],
            category = tokens[2],
            score = parseScore(tokens[3]),
            stock = parseStock(tokens[4]),
        )
        return CatalogCommand.Put(product)
    }

    private fun parseScore(token: String): Double {
        val score = token.toDouble()
        require(score.isFinite() && score in 0.0..1.0)
        return score
    }

    private fun parsePolicy(tokens: List<String>): CatalogCommand.Policy {
        require(tokens.size == 5)
        val stockTarget = tokens[3].toInt()
        require(stockTarget in 1..1000000)
        val policy = RecommendationPolicy(
            scoreWeight = parseScore(tokens[2]),
            stockTarget = stockTarget,
            minimumScore = parseScore(tokens[4]),
        )
        return CatalogCommand.Policy(tokens[1], policy)
    }

    private fun parseStock(token: String): Int {
        val stock = token.toInt()
        require(stock in 0..1000000)
        return stock
    }
}
