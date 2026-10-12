class PartitionMerge(val groups: List<IntRange>, val partitionCount: Int) {
    val reducesTasks: Boolean get() = groups.size < partitionCount
}

fun mergePartitions(sizes: LongArray, advisorySize: Long, minimumSize: Long): PartitionMerge {
    val groups = mutableListOf<IntRange>()
    var groupStart = 0
    var groupBytes = sizes[0]
    for (partition in 1 until sizes.size) {
        if (groupBytes >= minimumSize && groupBytes + sizes[partition] > advisorySize) {
            groups += groupStart until partition
            groupStart = partition
            groupBytes = sizes[partition]
        } else {
            groupBytes += sizes[partition]
        }
    }
    groups += groupStart until sizes.size
    if (groups.size >= 2 && groupBytes < minimumSize) {
        val last = groups.removeAt(groups.lastIndex)
        val previous = groups.removeAt(groups.lastIndex)
        groups += previous.first..last.last
    }
    return PartitionMerge(groups, sizes.size)
}
