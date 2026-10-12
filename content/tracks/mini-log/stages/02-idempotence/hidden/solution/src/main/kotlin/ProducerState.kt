private const val CACHED_BATCH_LIMIT = 5

class CachedBatch(val sequences: SequenceRange, val offsets: OffsetRange)

class ProducerState(val producerId: Long, firstBatch: CachedBatch) {
    private val _cachedBatches = ArrayDeque(listOf(firstBatch))

    var lastSequence: Int = firstBatch.sequences.last
        private set

    val cachedBatches: List<CachedBatch> get() = _cachedBatches

    fun findCached(sequences: SequenceRange): CachedBatch? = _cachedBatches.firstOrNull { it.sequences == sequences }

    fun remember(batch: CachedBatch) {
        _cachedBatches.addLast(batch)
        if (_cachedBatches.size > CACHED_BATCH_LIMIT) _cachedBatches.removeFirst()
        lastSequence = batch.sequences.last
    }
}

sealed interface Delivery {
    val offsets: OffsetRange

    data class Appended(override val offsets: OffsetRange) : Delivery

    data class Duplicate(override val offsets: OffsetRange) : Delivery
}
