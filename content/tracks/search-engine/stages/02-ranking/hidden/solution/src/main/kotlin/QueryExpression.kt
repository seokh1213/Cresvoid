sealed interface QueryExpression {
    data class Term(val tokens: List<String>) : QueryExpression
    data class Not(val operand: QueryExpression) : QueryExpression
    data class And(val operands: List<QueryExpression>) : QueryExpression
    data class Or(val operands: List<QueryExpression>) : QueryExpression
}
