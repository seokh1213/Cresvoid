import java.nio.file.Files
import java.nio.file.Path

class SegmentStore : AutoCloseable {
    private val directory = Files.createTempDirectory("catalog-segments-")
    private val segments = mutableMapOf<String, Path>()

    fun save(segment: String, payload: ByteArray) {
        val pending = Files.createTempFile(directory, "segment-", ".part")
        Files.write(pending, payload)
        val previous = segments.put(segment, pending)
        if (previous != null) Files.delete(previous)
    }

    fun describe(segment: String): String {
        val path = segments[segment] ?: return "MISSING $segment"
        val payload = Files.readAllBytes(path)
        return "PART $segment ${payload.size} ${encodeHex(payload)}"
    }

    override fun close() {
        segments.values.forEach { Files.delete(it) }
        Files.delete(directory)
    }

    private fun encodeHex(payload: ByteArray): String {
        if (payload.isEmpty()) return "-"
        val digits = "0123456789abcdef"
        return buildString(payload.size * 2) {
            payload.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(digits[unsigned ushr 4])
                append(digits[unsigned and 15])
            }
        }
    }
}
