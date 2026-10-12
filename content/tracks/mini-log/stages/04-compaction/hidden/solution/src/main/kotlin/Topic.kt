class Topic(val config: TopicConfig, partitionCount: Int) {
    private val partitions = ArrayList<Partition>()
    private val sticky = StickyPartition(config.batchSize)

    init {
        growTo(partitionCount)
    }

    val partitionCount: Int get() = partitions.size
    val isCompacted: Boolean get() = config.cleanupPolicy is CleanupPolicy.Compact

    fun requirePartition(index: Int) {
        if (index >= partitionCount) reject(ErrorCode.UNKNOWN_PARTITION)
    }

    fun partition(index: Int): Partition {
        requirePartition(index)
        return partitions[index]
    }

    fun growTo(newPartitionCount: Int) {
        repeat(newPartitionCount - partitions.size) { partitions += Partition(config.segmentBytes) }
    }

    fun produce(message: Message, now: Long): RecordLocation {
        val key = message.key
        val partition = if (key == null) sticky.partition else keyedPartition(key, partitionCount)
        val offsets = partitions[partition].log.append(listOf(message), now)
        if (key == null) sticky.recordAppended(message.sizeBytes, partitionCount)
        return RecordLocation(partition, offsets.first)
    }

    fun applyCleanupPolicy(now: Long) {
        when (val policy = config.cleanupPolicy) {
            is CleanupPolicy.Delete -> partitions.forEach { it.log.applyRetention(policy, now) }
            is CleanupPolicy.Compact -> partitions.forEach { it.compactor.compactIfDirty(policy, now) }
        }
    }
}
