sealed interface QueryExpression {
    data class Term(val tokens: List<String>) : QueryExpression
    data class Phrase(val tokens: List<String>) : QueryExpression
    data class Near(val left: String, val right: String, val maxDistance: Int) : QueryExpression
    data class Not(val operand: QueryExpression) : QueryExpression
    data class And(val operands: List<QueryExpression>) : QueryExpression
    data class Or(val operands: List<QueryExpression>) : QueryExpression
}
