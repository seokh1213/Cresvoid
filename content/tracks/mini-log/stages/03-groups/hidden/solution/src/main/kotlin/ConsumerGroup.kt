import java.util.TreeMap

class ConsumerGroup(
    val name: String,
    private val strategy: AssignmentStrategy,
    val topics: List<String>,
    val config: GroupConfig,
) {
    private val consumers = TreeMap<String, Consumer>()
    private val _committedOffsets = HashMap<TopicPartition, Long>()
    private var assignment: Map<String, List<TopicPartition>> = emptyMap()

    var generation = 0
        private set
    val committedOffsets: Map<TopicPartition, Long> get() = _committedOffsets

    val members: List<Consumer> get() = consumers.values.filter { it.state != ConsumerState.OutOfGroup }

    fun register(consumer: Consumer) {
        consumers[consumer.name] = consumer
    }

    fun unregister(consumer: Consumer) {
        consumers.remove(consumer.name)
    }

    fun rebalance(subscribedTopics: List<SubscribedTopic>) {
        generation++
        val memberNames = members.map { it.name }
        assignment = if (memberNames.isEmpty()) emptyMap() else strategy.assign(memberNames, subscribedTopics)
    }

    fun assignmentOf(consumer: String): List<TopicPartition> = assignment[consumer].orEmpty()

    fun commit(partition: TopicPartition, offset: Long) {
        _committedOffsets[partition] = offset
    }
}
