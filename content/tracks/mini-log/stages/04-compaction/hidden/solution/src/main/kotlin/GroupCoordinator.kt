import java.util.TreeSet

class PolledRecord(val partition: TopicPartition, val record: Record)

class CommittedOffset(val partition: TopicPartition, val offset: Long?, val logEndOffset: Long)

enum class TimeoutKind(val label: String) {
    POLL("poll"),
    SESSION("session"),
}

class Expiration(val deadline: Long, val group: String, val consumer: String, val kind: TimeoutKind)

private data class Deadline(val time: Long, val consumer: String) : Comparable<Deadline> {
    override fun compareTo(other: Deadline): Int = compareValuesBy(this, other, Deadline::time, Deadline::consumer)
}

class GroupCoordinator(private val topics: Map<String, Topic>) {
    private val groups = HashMap<String, ConsumerGroup>()
    private val consumers = HashMap<String, Consumer>()
    private val deadlines = TreeSet<Deadline>()

    fun createGroup(command: Command.CreateGroup) {
        if (command.group in groups) reject(ErrorCode.GROUP_EXISTS)
        if (command.topics.any { it !in topics }) reject(ErrorCode.UNKNOWN_TOPIC)
        groups[command.group] = ConsumerGroup(command.group, command.strategy, command.topics.sorted(), command.config)
    }

    fun join(groupName: String, consumerName: String, now: Long) {
        val group = group(groupName)
        if (consumerName in consumers) reject(ErrorCode.CONSUMER_EXISTS)
        val consumer = Consumer(consumerName, group)
        consumers[consumerName] = consumer
        group.register(consumer)
        syncMembership(consumer, now)
    }

    fun leave(consumerName: String) {
        val consumer = findRunningProcess(consumerName)
        val wasMember = consumer.state is ConsumerState.Running
        remove(consumer)
        if (wasMember) rebalance(consumer.group)
    }

    fun crash(consumerName: String, now: Long) {
        val consumer = findRunningProcess(consumerName)
        if (consumer.state is ConsumerState.Running) {
            changeState(consumer, ConsumerState.Crashed(now + consumer.group.config.sessionTimeoutMs))
        } else {
            remove(consumer)
        }
    }

    fun poll(consumerName: String, maxRecords: Int, now: Long): List<PolledRecord> {
        val consumer = findRunningProcess(consumerName)
        syncMembership(consumer, now)
        val assigned = consumer.group.assignmentOf(consumer.name)
        for (partition in assigned) consumer.seek(partition, startingPosition(consumer, partition))
        return fetchAssigned(consumer, assigned, maxRecords)
    }

    fun commit(consumerName: String) {
        val consumer = findRunningProcess(consumerName)
        if (consumer.state == ConsumerState.OutOfGroup) reject(ErrorCode.UNKNOWN_MEMBER)
        val group = consumer.group
        if (consumer.knownGeneration != group.generation) reject(ErrorCode.ILLEGAL_GENERATION)
        for (partition in group.assignmentOf(consumer.name)) {
            consumer.positions[partition]?.let { group.commit(partition, it) }
        }
    }

    fun group(name: String): ConsumerGroup = groups[name] ?: reject(ErrorCode.UNKNOWN_GROUP)

    fun committedOffsets(groupName: String): List<CommittedOffset> {
        val group = group(groupName)
        return subscribedTopics(group).flatMap { it.partitions }.map { partition ->
            CommittedOffset(partition, group.committedOffsets[partition], logOf(partition).logEndOffset)
        }
    }

    fun expireMembers(now: Long): List<Expiration> {
        val expirations = ArrayList<Expiration>()
        while (deadlines.isNotEmpty() && deadlines.first().time <= now) {
            val deadline = deadlines.first()
            val consumer = consumers.getValue(deadline.consumer)
            val kind = when (consumer.state) {
                is ConsumerState.Running -> TimeoutKind.POLL
                is ConsumerState.Crashed -> TimeoutKind.SESSION
                ConsumerState.OutOfGroup -> error("빠진 컨슈머에게는 마감 시각이 없다")
            }
            if (kind == TimeoutKind.POLL) changeState(consumer, ConsumerState.OutOfGroup) else remove(consumer)
            rebalance(consumer.group)
            expirations += Expiration(deadline.time, consumer.group.name, consumer.name, kind)
        }
        return expirations
    }

    fun rebalanceSubscribers(topic: String) {
        for (group in groups.values) {
            if (topic in group.topics && group.members.isNotEmpty()) rebalance(group)
        }
    }

    private fun findRunningProcess(consumerName: String): Consumer {
        val consumer = consumers[consumerName] ?: reject(ErrorCode.UNKNOWN_CONSUMER)
        if (consumer.state is ConsumerState.Crashed) reject(ErrorCode.NOT_RUNNING)
        return consumer
    }

    private fun syncMembership(consumer: Consumer, now: Long) {
        val group = consumer.group
        val rejoining = consumer.state == ConsumerState.OutOfGroup
        changeState(consumer, ConsumerState.Running(now + group.config.maxPollIntervalMs))
        if (rejoining) rebalance(group)
        if (consumer.knownGeneration != group.generation) consumer.adoptGeneration(group.generation)
    }

    private fun startingPosition(consumer: Consumer, partition: TopicPartition): Long {
        val log = logOf(partition)
        val reset = consumer.group.config.autoOffsetReset
        val position = consumer.positions[partition]
            ?: consumer.group.committedOffsets[partition]
            ?: reset.positionIn(log)
        return if (position < log.logStartOffset) reset.positionIn(log) else position
    }

    private fun fetchAssigned(consumer: Consumer, assigned: List<TopicPartition>, maxRecords: Int): List<PolledRecord> {
        val polled = ArrayList<PolledRecord>()
        for (partition in assigned) {
            if (polled.size == maxRecords) break
            val records = logOf(partition).read(consumer.positions.getValue(partition), maxRecords - polled.size)
            if (records.isEmpty()) continue
            records.mapTo(polled) { PolledRecord(partition, it) }
            consumer.seek(partition, records.last().offset + 1)
        }
        return polled
    }

    private fun changeState(consumer: Consumer, state: ConsumerState) {
        forgetDeadline(consumer)
        consumer.state = state
        state.deadline?.let { deadlines.add(Deadline(it, consumer.name)) }
    }

    private fun remove(consumer: Consumer) {
        forgetDeadline(consumer)
        consumers.remove(consumer.name)
        consumer.group.unregister(consumer)
    }

    private fun forgetDeadline(consumer: Consumer) {
        consumer.state.deadline?.let { deadlines.remove(Deadline(it, consumer.name)) }
    }

    private fun rebalance(group: ConsumerGroup) {
        group.rebalance(subscribedTopics(group))
    }

    private fun subscribedTopics(group: ConsumerGroup): List<SubscribedTopic> =
        group.topics.map { SubscribedTopic(it, topics.getValue(it).partitionCount) }

    private fun logOf(partition: TopicPartition): PartitionLog =
        topics.getValue(partition.topic).partition(partition.partition).log
}
