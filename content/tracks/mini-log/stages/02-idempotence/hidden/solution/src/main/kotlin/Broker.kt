class Broker(private val emit: (String) -> Unit) {
    private val topics = HashMap<String, Topic>()
    private val producerClients = ProducerClients()
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
            is Command.ShowOffsets -> emit(offsetsLine(findPartition(command.target).log))
            is Command.ShowSegments -> showSegments(command.target)
            is Command.Tick -> tick(command.millis)
            is Command.InitProducer -> emit(producerIdLine(producerClients.start(command.producer)))
            is Command.CreateBatch -> createBatch(command)
            is Command.Deliver -> deliver(command.batch)
            is Command.ShowProducers -> showProducers(command.target)
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
        val log = findPartition(command.target).log
        if (command.offset !in log.logStartOffset..log.logEndOffset) reject(ErrorCode.OFFSET_OUT_OF_RANGE)
        val records = log.read(command.offset, command.maxRecords)
        if (records.isEmpty()) emit(EMPTY_LINE) else records.forEach { emit(recordLine(it)) }
    }

    private fun showSegments(target: TopicPartition) {
        val segments = findPartition(target).log.segments
        segments.forEachIndexed { index, segment -> emit(segmentLine(segment, isActive = index == segments.lastIndex)) }
    }

    private fun tick(millis: Long) {
        now += millis
        for (topic in topics.values) topic.applyRetention(now)
    }

    private fun createBatch(command: Command.CreateBatch) {
        val session = producerClients.session(command.producer)
        producerClients.requireNewBatchName(command.batch)
        val topic = findTopic(command.target.topic)
        topic.requirePartition(command.target.partition)
        if (command.messages.sumOf { it.sizeBytes } > topic.config.segmentBytes) reject(ErrorCode.RECORD_TOO_LARGE)
        emit(batchLine(producerClients.createBatch(command.batch, session, command.target, command.messages)))
    }

    private fun deliver(batchName: String) {
        val batch = producerClients.batch(batchName)
        emit(deliveryLine(findPartition(batch.target).receive(batch, now)))
    }

    private fun showProducers(target: TopicPartition) {
        val producers = findPartition(target).producers
        if (producers.isEmpty()) emit(NONE_LINE) else producers.forEach { emit(producerStateLine(it)) }
    }

    private fun findTopic(name: String): Topic = topics[name] ?: reject(ErrorCode.UNKNOWN_TOPIC)

    private fun findPartition(target: TopicPartition): Partition = findTopic(target.topic).partition(target.partition)
}
