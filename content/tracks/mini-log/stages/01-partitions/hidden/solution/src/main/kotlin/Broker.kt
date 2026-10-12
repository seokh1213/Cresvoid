class Broker(private val emit: (String) -> Unit) {
    private val topics = HashMap<String, Topic>()
    private var now = 0L

    fun handle(line: String) {
        try {
            execute(parseCommand(line))
        } catch (failure: CommandFailure) {
            emit(failure.line)
        }
    }

    private fun execute(command: Command) {
        when (command) {
            is Command.CreateTopic -> createTopic(command)
            is Command.AddPartitions -> addPartitions(command)
            is Command.Produce -> produce(command)
            is Command.Fetch -> fetch(command)
            is Command.ShowOffsets -> emit(offsetsLine(findLog(command.target)))
            is Command.ShowSegments -> showSegments(command.target)
            is Command.Tick -> tick(command.millis)
        }
    }

    private fun createTopic(command: Command.CreateTopic) {
        if (command.topic in topics) reject(ErrorCode.TOPIC_EXISTS)
        topics[command.topic] = Topic(command.config, command.partitionCount)
    }

    private fun addPartitions(command: Command.AddPartitions) {
        val topic = findTopic(command.topic)
        if (command.partitionCount <= topic.partitionCount) reject(ErrorCode.INVALID_PARTITIONS)
        topic.growTo(command.partitionCount)
    }

    private fun produce(command: Command.Produce) {
        val topic = findTopic(command.topic)
        if (command.message.sizeBytes > topic.config.segmentBytes) reject(ErrorCode.RECORD_TOO_LARGE)
        emit(recordLocationLine(topic.produce(command.message, now)))
    }

    private fun fetch(command: Command.Fetch) {
        val log = findLog(command.target)
        if (command.offset !in log.logStartOffset..log.logEndOffset) reject(ErrorCode.OFFSET_OUT_OF_RANGE)
        val records = log.read(command.offset, command.maxRecords)
        if (records.isEmpty()) emit(EMPTY_LINE) else records.forEach { emit(recordLine(it)) }
    }

    private fun showSegments(target: TopicPartition) {
        val segments = findLog(target).segments
        segments.forEachIndexed { index, segment -> emit(segmentLine(segment, isActive = index == segments.lastIndex)) }
    }

    private fun tick(millis: Long) {
        now += millis
        for (topic in topics.values) topic.applyRetention(now)
    }

    private fun findTopic(name: String): Topic = topics[name] ?: reject(ErrorCode.UNKNOWN_TOPIC)

    private fun findLog(target: TopicPartition): PartitionLog = findTopic(target.topic).log(target.partition)
}
