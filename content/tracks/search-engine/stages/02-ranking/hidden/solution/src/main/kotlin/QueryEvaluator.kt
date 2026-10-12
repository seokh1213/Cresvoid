class QueryEvaluator(private val index: InvertedIndex) {

    fun matchingDocuments(expression: QueryExpression): Set<DocumentId> =
        matchesAmong(index.documentIds, expression)

    private fun matchesAmong(candidates: Set<DocumentId>, expression: QueryExpression): Set<DocumentId> =
        when (expression) {
            is QueryExpression.Term -> expression.tokens.fold(candidates) { matches, token ->
                intersection(matches, index.postings(token))
            }
            is QueryExpression.Not -> candidates - matchesAmong(candidates, expression.operand)
            is QueryExpression.And -> expression.operands
                .sortedBy(::estimatedMatchCount)
                .fold(candidates, ::matchesAmong)
            is QueryExpression.Or -> expression.operands.flatMapTo(HashSet()) { matchesAmong(candidates, it) }
        }

    private fun estimatedMatchCount(expression: QueryExpression): Int = when (expression) {
        is QueryExpression.Term -> expression.tokens.minOf { index.postings(it).size }
        is QueryExpression.Not -> index.documentCount
        is QueryExpression.And -> expression.operands.minOf(::estimatedMatchCount)
        is QueryExpression.Or -> expression.operands.sumOf(::estimatedMatchCount)
    }
}

private fun intersection(first: Set<DocumentId>, second: Set<DocumentId>): Set<DocumentId> {
    val (smaller, larger) = if (first.size <= second.size) first to second else second to first
    return smaller.filterTo(HashSet()) { it in larger }
}
