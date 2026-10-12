sealed interface ConsumerState {
    val deadline: Long?

    data class Running(override val deadline: Long) : ConsumerState

    data class Crashed(override val deadline: Long) : ConsumerState

    data object OutOfGroup : ConsumerState {
        override val deadline: Long? = null
    }
}

class Consumer(val name: String, val group: ConsumerGroup) {
    private val _positions = HashMap<TopicPartition, Long>()

    var state: ConsumerState = ConsumerState.OutOfGroup
    var knownGeneration = 0
        private set
    val positions: Map<TopicPartition, Long> get() = _positions

    fun seek(partition: TopicPartition, offset: Long) {
        _positions[partition] = offset
    }

    fun adoptGeneration(generation: Int) {
        _positions.clear()
        knownGeneration = generation
    }
}
