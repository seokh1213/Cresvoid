import kotlin.random.Random

@Suppress("UNUSED_PARAMETER")
fun candidatePairs(groups: List<ExactGroup>, threshold: Threshold): List<GroupPair> {
    val random = Random(901)
    val partitions = groups.map { random.nextInt(4) }
    val candidates = mutableListOf<GroupPair>()
    for (left in groups.indices) {
        for (right in left + 1 until groups.size) {
            if (partitions[left] == partitions[right]) {
                candidates.add(GroupPair(left, right))
            }
        }
    }
    return candidates
}
