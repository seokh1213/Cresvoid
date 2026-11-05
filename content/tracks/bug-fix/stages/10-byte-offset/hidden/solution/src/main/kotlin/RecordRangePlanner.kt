data class RecordRange(val start: Long, val end: Long)

class RecordRangePlanner(private val files: CatalogFileStore) {
    fun plan(id: String, batchSize: Int): List<RecordRange> {
        require(batchSize > 0)
        val content = files.text(id)
        if (content.isEmpty()) return emptyList()

        val ranges = mutableListOf<RecordRange>()
        var cursor = 0
        var position = 0L
        var rangeStart = 0L
        var recordCount = 0
        while (cursor < content.length) {
            val lineFeed = content.indexOf('\n', cursor)
            val nextCursor = if (lineFeed < 0) content.length else lineFeed + 1
            val lineEnd = when {
                lineFeed < 0 -> content.length
                lineFeed > cursor && content[lineFeed - 1] == '\r' -> lineFeed - 1
                else -> lineFeed
            }
            val record = content.substring(cursor, lineEnd)
            val terminatorSize = nextCursor - lineEnd
            position += record.toByteArray(Charsets.UTF_8).size + terminatorSize
            recordCount++
            cursor = nextCursor

            if (recordCount == batchSize && cursor < content.length) {
                ranges += RecordRange(rangeStart, position)
                rangeStart = position
                recordCount = 0
            }
        }
        ranges += RecordRange(rangeStart, files.size(id))
        return ranges
    }
}
