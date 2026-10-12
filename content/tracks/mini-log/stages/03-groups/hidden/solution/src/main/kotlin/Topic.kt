class Topic(val config: TopicConfig, partitionCount: Int) {
    private val partitions = ArrayList<Partition>()
    private val sticky = StickyPartition(config.batchSize)

    init {
        growTo(partitionCount)
    }

    val partitionCount: Int get() = partitions.size

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

    fun applyRetention(now: Long) {
        for (partition in partitions) partition.log.applyRetention(config, now)
    }
}
