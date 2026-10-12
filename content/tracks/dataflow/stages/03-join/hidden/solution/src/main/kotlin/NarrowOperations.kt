fun Partition.withKeyPrefix(length: Int, dictionary: KeyDictionary): Partition =
    withKeys(IntArray(size) { dictionary.prefix(keys[it], length) })

inline fun Partition.mapNumbers(operation: (Long) -> Long): Partition {
    val numbers = (values as Numbers).numbers
    return withValues(Numbers(LongArray(size) { operation(numbers[it]) }))
}

fun Partition.summarize(summary: Summary): Partition {
    val summarized = when (val current = values) {
        is Lists -> LongArray(size) { current.summarize(it, summary) }
        is Pairs -> LongArray(size) { current.summarize(it, summary) }
        is Numbers -> error("a number value has no summary")
    }
    return withValues(Numbers(summarized))
}

inline fun Partition.keepRows(predicate: (Int) -> Boolean): Partition {
    val kept = IntArray(size)
    var count = 0
    for (row in 0 until size) {
        if (predicate(row)) kept[count++] = row
    }
    return if (count == size) this else gather(kept, 0, count)
}

fun coalesce(partitions: List<Partition>, target: Int): List<Partition> {
    val parents = partitions.size
    if (target >= parents) return partitions
    return List(target) { child -> Partition.concat(partitions.subList(child * parents / target, (child + 1) * parents / target)) }
}

private fun Lists.summarize(row: Int, summary: Summary): Long {
    val reducer = summary.reducer ?: return length(row).toLong()
    var result = elements[starts[row]]
    for (index in starts[row] + 1 until starts[row + 1]) result = reducer.combine(result, elements[index])
    return result
}

private fun Pairs.summarize(row: Int, summary: Summary): Long {
    val reducer = summary.reducer ?: error("a pair value has no count")
    return reducer.combine(left[row], right[row])
}
