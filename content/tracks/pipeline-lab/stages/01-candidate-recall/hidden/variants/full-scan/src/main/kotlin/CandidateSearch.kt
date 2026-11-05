@Suppress("UNUSED_PARAMETER")
fun candidatePairs(groups: List<ExactGroup>, threshold: Threshold): List<GroupPair> =
    groups.indices.flatMap { left ->
        (left + 1 until groups.size).map { right -> GroupPair(left, right) }
    }
