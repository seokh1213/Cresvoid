
fun candidatePairs(groups: List<ExactGroup>, threshold: Threshold): List<GroupPair> {
    val candidates = mutableListOf<GroupPair>()
    for (left in groups.indices) {
        for (right in left + 1 until groups.size) {
            val a = groups[left].key
            val b = groups[right].key
            val fitsSize = minOf(a.tokens.size, b.tokens.size).toLong() * threshold.denominator >=
                maxOf(a.tokens.size, b.tokens.size).toLong() * threshold.numerator
            if (a.category == b.category && fitsSize && right - left <= 4) {
                candidates.add(GroupPair(left, right))
            }
        }
    }
    return candidates
}
