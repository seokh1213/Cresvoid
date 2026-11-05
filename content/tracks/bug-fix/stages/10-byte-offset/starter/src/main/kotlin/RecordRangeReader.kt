import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

class RecordRangeReader(private val files: CatalogFileStore) {
    fun read(id: String, range: RecordRange): List<String> {
        require(range.start >= 0 && range.end >= range.start)
        require(range.end <= files.size(id))
        if (range.start == range.end) return emptyList()

        val bytes = files.open(id).use { input ->
            input.skipNBytes(range.start)
            readRange(input, range.end - range.start)
        }
        val content = bytes.toString(Charsets.UTF_8)
        return splitRecords(content)
    }

    private fun readRange(input: InputStream, size: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var remaining = size
        while (remaining > 0) {
            val requested = minOf(remaining, buffer.size.toLong()).toInt()
            val count = input.read(buffer, 0, requested)
            if (count < 0) throw EOFException("Export ended inside a range")
            if (count == 0) continue
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    private fun splitRecords(content: String): List<String> {
        val records = mutableListOf<String>()
        var cursor = 0
        while (cursor < content.length) {
            val lineFeed = content.indexOf('\n', cursor)
            if (lineFeed < 0) {
                records += content.substring(cursor)
                break
            }
            val recordEnd = if (lineFeed > cursor && content[lineFeed - 1] == '\r') {
                lineFeed - 1
            } else {
                lineFeed
            }
            records += content.substring(cursor, recordEnd)
            cursor = lineFeed + 1
        }
        return records
    }
}
