fun findMatches(batch: Batch): MatchResult {
    val groups = batch.records.groupBy { ExactKey(it.category, it.tokens) }
        .map { (key, records) -> ExactGroup(key, records.map { it.id }.sorted()) }
    val pairs = sortedSetOf<IdPair>()
    for (group in groups) {
        for (left in group.ids.indices) {
            for (right in left + 1 until group.ids.size) {
                pairs.add(IdPair(group.ids[left], group.ids[right]))
            }
        }
    }
    val similarity = Similarity(batch.threshold)
    for (candidate in candidatePairs(groups, batch.threshold)) {
        require(candidate.left in groups.indices && candidate.right in candidate.left + 1 until groups.size)
        val left = groups[candidate.left]
        val right = groups[candidate.right]
        if (similarity.matches(left.key, right.key)) expandPairs(left, right, pairs)
    }
    return MatchResult(pairs.toList(), similarity.comparisons)
}

private fun expandPairs(left: ExactGroup, right: ExactGroup, pairs: MutableSet<IdPair>) {
    for (leftId in left.ids) {
        for (rightId in right.ids) {
            pairs.add(IdPair(minOf(leftId, rightId), maxOf(leftId, rightId)))
        }
    }
}
