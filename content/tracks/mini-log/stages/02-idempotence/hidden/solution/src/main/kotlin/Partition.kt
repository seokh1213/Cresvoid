import java.util.TreeMap

class Partition(segmentBytes: Long) {
    val log = PartitionLog(segmentBytes)
    private val producerStates = TreeMap<Long, ProducerState>()

    val producers: Collection<ProducerState> get() = producerStates.values

    fun receive(batch: ProducerBatch, now: Long): Delivery {
        val state = producerStates[batch.producerId]
        val duplicate = state?.findCached(batch.sequences)
        if (duplicate != null) return Delivery.Duplicate(duplicate.offsets)
        val expectedSequence = if (state == null) 0 else state.lastSequence + 1
        if (batch.sequences.first != expectedSequence) {
            reject(ErrorCode.OUT_OF_ORDER_SEQUENCE, "expected=$expectedSequence")
        }
        val appended = CachedBatch(batch.sequences, log.append(batch.messages, now))
        if (state == null) {
            producerStates[batch.producerId] = ProducerState(batch.producerId, appended)
        } else {
            state.remember(appended)
        }
        return Delivery.Appended(appended.offsets)
    }
}
