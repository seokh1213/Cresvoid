data class RecommendationPolicy(
    val scoreWeight: Double = 1.0,
    val stockTarget: Int = 1,
    val minimumScore: Double = 0.0,
) {
    fun evaluate(product: Product): RecommendationCandidate {
        val stockSignal = product.stock.coerceAtMost(stockTarget).toDouble() / stockTarget
        val catalogContribution = product.score * scoreWeight
        val stockContribution = stockSignal * (1.0 - scoreWeight)
        return RecommendationCandidate(product, catalogContribution + stockContribution)
    }
}

data class RecommendationCandidate(
    val product: Product,
    val score: Double,
)

class RecommendationPolicies {
    private val policies = mutableMapOf<String, RecommendationPolicy>()
    private val defaultPolicy = RecommendationPolicy()

    fun replace(category: String, policy: RecommendationPolicy) {
        policies[category] = policy
    }

    fun forCategory(category: String): RecommendationPolicy = policies[category] ?: defaultPolicy
}
