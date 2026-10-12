data class SequenceRange(val first: Int, val last: Int)

class ProducerBatch(
    val producerId: Long,
    val sequences: SequenceRange,
    val target: TopicPartition,
    val messages: List<Message>,
)

class ProducerSession(val producerId: Long) {
    private val nextSequences = HashMap<TopicPartition, Int>()

    fun takeSequences(target: TopicPartition, recordCount: Int): SequenceRange {
        val first = nextSequences[target] ?: 0
        val sequences = SequenceRange(first, first + recordCount - 1)
        nextSequences[target] = sequences.last + 1
        return sequences
    }
}

class ProducerClients {
    private var lastProducerId = 0L
    private val sessions = HashMap<String, ProducerSession>()
    private val batches = HashMap<String, ProducerBatch>()

    fun start(producer: String): ProducerSession {
        val session = ProducerSession(++lastProducerId)
        sessions[producer] = session
        return session
    }

    fun session(producer: String): ProducerSession = sessions[producer] ?: reject(ErrorCode.UNKNOWN_PRODUCER)

    fun requireNewBatchName(batch: String) {
        if (batch in batches) reject(ErrorCode.BATCH_EXISTS)
    }

    fun createBatch(
        name: String,
        session: ProducerSession,
        target: TopicPartition,
        messages: List<Message>,
    ): ProducerBatch {
        val batch = ProducerBatch(session.producerId, session.takeSequences(target, messages.size), target, messages)
        batches[name] = batch
        return batch
    }

    fun batch(name: String): ProducerBatch = batches[name] ?: reject(ErrorCode.UNKNOWN_BATCH)
}
