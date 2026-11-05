class Recommendations(
    private val catalog: Catalog,
    private val policies: RecommendationPolicies = RecommendationPolicies(),
    private val order: Comparator<RecommendationCandidate> = RecommendationOrder(),
) {
    fun configure(category: String, policy: RecommendationPolicy) {
        policies.replace(category, policy)
    }

    fun show(category: String, limit: Int): List<Product> {
        val policy = policies.forCategory(category)
        val candidates = catalog.availableIn(category)
            .map { policy.evaluate(it) }
            .filter { it.score >= policy.minimumScore }
        val ranked = candidates.sortedWith(order)
        return ranked.take(limit).map { it.product }
    }
}

class RecommendationOrder : Comparator<RecommendationCandidate> {
    override fun compare(first: RecommendationCandidate, second: RecommendationCandidate): Int {
        val scoreOrder = java.lang.Double.compare(second.score, first.score)
        if (scoreOrder != 0) return scoreOrder
        return first.product.id.compareTo(second.product.id)
    }
}
