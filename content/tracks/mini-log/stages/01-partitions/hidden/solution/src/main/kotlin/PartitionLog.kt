class PartitionLog(private val segmentBytes: Long) {
    private val _segments = ArrayDeque(listOf(Segment(baseOffset = 0)))

    var logStartOffset: Long = 0
        private set
    var logEndOffset: Long = 0
        private set
    var sizeBytes: Long = 0
        private set

    val segments: List<Segment> get() = _segments

    private val activeSegment: Segment get() = _segments.last()

    fun append(message: Message, timestamp: Long): Long {
        rollIfFull(message.sizeBytes)
        val record = Record(logEndOffset, message, timestamp)
        activeSegment.append(record)
        sizeBytes += message.sizeBytes
        logEndOffset++
        return record.offset
    }

    fun read(fromOffset: Long, maxRecords: Int): List<Record> =
        _segments.subList(segmentIndexFor(fromOffset), _segments.size).asSequence()
            .flatMap { it.recordsFrom(fromOffset) }
            .take(maxRecords)
            .toList()

    fun deleteOldestSegmentsWhile(shouldDelete: (Segment) -> Boolean) {
        while (_segments.size > 1 && shouldDelete(_segments.first())) {
            sizeBytes -= _segments.removeFirst().sizeBytes
            logStartOffset = _segments.first().baseOffset
        }
    }

    private fun rollIfFull(incomingBytes: Long) {
        val active = activeSegment
        if (!active.isEmpty() && active.sizeBytes + incomingBytes > segmentBytes) {
            _segments.addLast(Segment(logEndOffset))
        }
    }

    private fun segmentIndexFor(offset: Long): Int {
        val found = _segments.binarySearchBy(offset) { it.baseOffset }
        return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
    }
}
