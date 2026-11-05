sealed interface SearchQuery {
    data class Term(val text: String) : SearchQuery
    data class Phrase(val words: List<String>) : SearchQuery
    data class Price(
        val lower: Long?,
        val upper: Long?,
    ) : SearchQuery

    data class Not(val operand: SearchQuery) : SearchQuery

    data class And(
        val left: SearchQuery,
        val right: SearchQuery,
    ) : SearchQuery

    data class Or(
        val left: SearchQuery,
        val right: SearchQuery,
    ) : SearchQuery
}

object QueryOutput {
    fun render(query: SearchQuery): String =
        "TREE ${tree(query)} | QUERY ${normalized(query)}"

    fun tree(query: SearchQuery): String = when (query) {
        is SearchQuery.Term -> "TERM(${query.text})"
        is SearchQuery.Phrase -> "PHRASE(\"${query.words.joinToString(" ")}\")"
        is SearchQuery.Price -> "PRICE(${query.lower ?: "*"},${query.upper ?: "*"})"
        is SearchQuery.Not -> "NOT(${tree(query.operand)})"
        is SearchQuery.And -> "AND(${tree(query.left)},${tree(query.right)})"
        is SearchQuery.Or -> "OR(${tree(query.left)},${tree(query.right)})"
    }

    fun normalized(query: SearchQuery): String = when (query) {
        is SearchQuery.Term -> query.text
        is SearchQuery.Phrase -> "\"${query.words.joinToString(" ")}\""
        is SearchQuery.Price -> "price:${query.lower ?: ""}..${query.upper ?: ""}"
        is SearchQuery.Not -> "NOT ${normalized(query.operand)}"
        is SearchQuery.And -> "(${normalized(query.left)} AND ${normalized(query.right)})"
        is SearchQuery.Or -> "(${normalized(query.left)} OR ${normalized(query.right)})"
    }
}
