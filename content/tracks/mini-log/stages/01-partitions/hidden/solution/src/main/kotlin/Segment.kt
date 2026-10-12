class Segment(val baseOffset: Long) {
    private val records = ArrayList<Record>()

    var sizeBytes: Long = 0
        private set
    var maxTimestamp: Long? = null
        private set

    val recordCount: Int get() = records.size

    fun isEmpty(): Boolean = records.isEmpty()

    fun append(record: Record) {
        records += record
        sizeBytes += record.message.sizeBytes
        maxTimestamp = maxOf(maxTimestamp ?: record.timestamp, record.timestamp)
    }

    fun recordsFrom(offset: Long): List<Record> {
        val found = records.binarySearchBy(offset) { it.offset }
        val start = if (found >= 0) found else -found - 1
        return records.subList(start, records.size)
    }

    fun isExpired(now: Long, retentionMs: Long): Boolean {
        val newest = maxTimestamp ?: return false
        return now - newest > retentionMs
    }
}
