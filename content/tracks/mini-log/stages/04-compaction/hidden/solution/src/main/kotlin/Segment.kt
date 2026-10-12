class Segment(val baseOffset: Long) {
    private val _records = ArrayList<Record>()

    var sizeBytes: Long = 0
        private set
    var maxTimestamp: Long? = null
        private set

    val records: List<Record> get() = _records
    val recordCount: Int get() = _records.size

    fun isEmpty(): Boolean = _records.isEmpty()

    fun append(record: Record) {
        _records += record
        sizeBytes += record.message.sizeBytes
        maxTimestamp = maxOf(maxTimestamp ?: record.timestamp, record.timestamp)
    }

    fun recordsFrom(offset: Long): List<Record> {
        val found = _records.binarySearchBy(offset) { it.offset }
        val start = if (found >= 0) found else -found - 1
        return _records.subList(start, _records.size)
    }

    fun isExpired(now: Long, retentionMs: Long): Boolean {
        val newest = maxTimestamp ?: return false
        return now - newest > retentionMs
    }
}
