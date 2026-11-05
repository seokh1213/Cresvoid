class Similarity(private val threshold: Threshold) {
    var comparisons: Long = 0
        private set

    fun matches(left: ExactKey, right: ExactKey): Boolean {
        comparisons++
        if (left.category != right.category) return false
        if (left.tokens.isEmpty() && right.tokens.isEmpty()) return true
        val rightTokens = right.tokens.toHashSet()
        val intersection = left.tokens.count { it in rightTokens }.toLong()
        val union = left.tokens.size + right.tokens.size - intersection
        return intersection * threshold.denominator >= union * threshold.numerator
    }
}
