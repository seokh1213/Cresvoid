class Broker(private val emit: (String) -> Unit) {
    private val topics = HashMap<String, Topic>()
    private val producerClients = ProducerClients()
    private val coordinator = GroupCoordinator(topics)
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
            is Command.CreateGroup -> coordinator.createGroup(command)
            is Command.Join -> coordinator.join(command.group, command.consumer, now)
            is Command.Leave -> coordinator.leave(command.consumer)
            is Command.Crash -> coordinator.crash(command.consumer, now)
            is Command.Poll -> showPolled(coordinator.poll(command.consumer, command.maxRecords, now))
            is Command.Commit -> coordinator.commit(command.consumer)
            is Command.ShowAssignment -> assignmentLines(coordinator.group(command.group)).forEach(emit)
            is Command.ShowCommitted -> coordinator.committedOffsets(command.group).forEach { emit(committedLine(it)) }
            is Command.ShowDirty -> showDirty(command.target)
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
        coordinator.rebalanceSubscribers(command.topic)
    }

    private fun produce(command: Command.Produce) {
        val topic = findTopic(command.topic)
        if (topic.isCompacted && command.message.key == null) reject(ErrorCode.KEY_REQUIRED)
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
        coordinator.expireMembers(now).forEach { emit(expirationLine(it)) }
        for (topic in topics.values) topic.applyCleanupPolicy(now)
    }

    private fun createBatch(command: Command.CreateBatch) {
        val session = producerClients.session(command.producer)
        producerClients.requireNewBatchName(command.batch)
        val topic = findTopic(command.target.topic)
        topic.requirePartition(command.target.partition)
        if (topic.isCompacted && command.messages.any { it.key == null }) reject(ErrorCode.KEY_REQUIRED)
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

    private fun showDirty(target: TopicPartition) {
        val topic = findTopic(target.topic)
        val partition = topic.partition(target.partition)
        if (!topic.isCompacted) reject(ErrorCode.NOT_COMPACTED)
        emit(dirtyLine(partition.compactor.dirtyStats()))
    }

    private fun showPolled(polled: List<PolledRecord>) {
        if (polled.isEmpty()) emit(EMPTY_LINE) else polled.forEach { emit(polledLine(it)) }
    }

    private fun findTopic(name: String): Topic = topics[name] ?: reject(ErrorCode.UNKNOWN_TOPIC)

    private fun findPartition(target: TopicPartition): Partition = findTopic(target.topic).partition(target.partition)
}
