class Topic(val config: TopicConfig, partitionCount: Int) {
    private val logs = ArrayList<PartitionLog>()
    private val sticky = StickyPartition(config.batchSize)

    init {
        growTo(partitionCount)
    }

    val partitionCount: Int get() = logs.size

    fun log(partition: Int): PartitionLog = logs.getOrNull(partition) ?: reject(ErrorCode.UNKNOWN_PARTITION)

    fun growTo(newPartitionCount: Int) {
        repeat(newPartitionCount - logs.size) { logs += PartitionLog(config.segmentBytes) }
    }

    fun produce(message: Message, now: Long): RecordLocation {
        val key = message.key
        val partition = if (key == null) sticky.partition else keyedPartition(key, partitionCount)
        val offset = logs[partition].append(message, now)
        if (key == null) sticky.recordAppended(message.sizeBytes, partitionCount)
        return RecordLocation(partition, offset)
    }

    fun applyRetention(now: Long) {
        for (log in logs) log.applyRetention(config, now)
    }
}
