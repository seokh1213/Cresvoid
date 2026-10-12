sealed interface Command {
    data class CreateTopic(val topic: String, val partitionCount: Int, val config: TopicConfig) : Command

    data class AddPartitions(val topic: String, val partitionCount: Int) : Command

    data class Produce(val topic: String, val message: Message) : Command

    data class Fetch(val target: TopicPartition, val offset: Long, val maxRecords: Int) : Command

    data class ShowOffsets(val target: TopicPartition) : Command

    data class ShowSegments(val target: TopicPartition) : Command

    data class Tick(val millis: Long) : Command

    data class InitProducer(val producer: String) : Command

    data class CreateBatch(
        val producer: String,
        val batch: String,
        val target: TopicPartition,
        val messages: List<Message>,
    ) : Command

    data class Deliver(val batch: String) : Command

    data class ShowProducers(val target: TopicPartition) : Command
}
