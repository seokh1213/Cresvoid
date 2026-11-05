import kotlin.random.Random

fun candidatePairs(groups: List<ExactGroup>, threshold: Threshold): List<GroupPair> {
    val random = Random(901)
    val partitions = groups.map { random.nextInt(4) }
    val candidates = mutableListOf<GroupPair>()
    for (left in groups.indices) {
        for (right in left + 1 until groups.size) {
            val a = groups[left].key
            val b = groups[right].key
            val fitsSize = minOf(a.tokens.size, b.tokens.size).toLong() * threshold.denominator >=
                maxOf(a.tokens.size, b.tokens.size).toLong() * threshold.numerator
            if (a.category == b.category && fitsSize && partitions[left] == partitions[right]) {
                candidates.add(GroupPair(left, right))
            }
        }
    }
    return candidates
}
