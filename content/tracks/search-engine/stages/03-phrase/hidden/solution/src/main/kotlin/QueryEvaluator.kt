class QueryEvaluator(private val index: InvertedIndex) {

    fun matchingDocuments(expression: QueryExpression): Set<DocumentId> =
        matchesAmong(index.documentIds, expression)

    private fun matchesAmong(candidates: Set<DocumentId>, expression: QueryExpression): Set<DocumentId> =
        when (expression) {
            is QueryExpression.Term -> containingAll(candidates, expression.tokens)
            is QueryExpression.Phrase -> containingAll(candidates, expression.tokens)
                .filterTo(HashSet()) { id -> index.document(id).containsPhrase(expression.tokens) }
            is QueryExpression.Near -> containingAll(candidates, listOf(expression.left, expression.right))
                .filterTo(HashSet()) { id ->
                    index.document(id).containsNear(expression.left, expression.right, expression.maxDistance)
                }
            is QueryExpression.Not -> candidates - matchesAmong(candidates, expression.operand)
            is QueryExpression.And -> expression.operands
                .sortedBy(::estimatedMatchCount)
                .fold(candidates, ::matchesAmong)
            is QueryExpression.Or -> expression.operands.flatMapTo(HashSet()) { matchesAmong(candidates, it) }
        }

    private fun containingAll(candidates: Set<DocumentId>, tokens: List<String>): Set<DocumentId> =
        tokens.fold(candidates) { matches, token -> intersection(matches, index.postings(token)) }

    private fun estimatedMatchCount(expression: QueryExpression): Int = when (expression) {
        is QueryExpression.Term -> smallestPostingsSize(expression.tokens)
        is QueryExpression.Phrase -> smallestPostingsSize(expression.tokens)
        is QueryExpression.Near -> smallestPostingsSize(listOf(expression.left, expression.right))
        is QueryExpression.Not -> index.documentCount
        is QueryExpression.And -> expression.operands.minOf(::estimatedMatchCount)
        is QueryExpression.Or -> expression.operands.sumOf(::estimatedMatchCount)
    }

    private fun smallestPostingsSize(tokens: List<String>): Int = tokens.minOf { index.postings(it).size }
}

private fun intersection(first: Set<DocumentId>, second: Set<DocumentId>): Set<DocumentId> {
    val (smaller, larger) = if (first.size <= second.size) first to second else second to first
    return smaller.filterTo(HashSet()) { it in larger }
}
