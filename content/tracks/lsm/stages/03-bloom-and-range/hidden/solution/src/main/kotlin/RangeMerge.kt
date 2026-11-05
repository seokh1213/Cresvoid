import java.util.PriorityQueue

private data class CursorHead(val record: Record, val remaining: Iterator<Record>)

class RangeMerge(private val upper: String) {
    private val heads = PriorityQueue<CursorHead>(compareBy { it.record.key })

    fun read(sources: List<Iterator<Record>>, limit: Int): List<Record> {
        sources.forEach(::advance)
        val rows = mutableListOf<Record>()
        while (heads.isNotEmpty() && rows.size < limit) {
            val key = heads.peek().record.key
            var newest = takeNext()
            while (heads.isNotEmpty() && heads.peek().record.key == key) {
                val candidate = takeNext()
                if (candidate.sequence > newest.sequence) newest = candidate
            }
            if (newest.payload is Payload.Value) rows.add(newest)
        }
        return rows
    }

    private fun takeNext(): Record {
        val head = heads.remove()
        advance(head.remaining)
        return head.record
    }

    private fun advance(records: Iterator<Record>) {
        if (!records.hasNext()) return
        val record = records.next()
        if (record.key <= upper) heads.add(CursorHead(record, records))
    }
}
