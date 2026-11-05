import java.io.InputStream
import java.util.Objects

class TransferSources {
    private val sources = mutableMapOf<String, TransferSource>()

    fun register(name: String, content: ByteArray, responseLimits: List<Int>) {
        require(responseLimits.isNotEmpty() && responseLimits.all { it > 0 })
        sources[name] = TransferSource(content.copyOf(), responseLimits.toList())
    }

    fun openAt(name: String, start: Int): InputStream {
        val source = sources.getValue(name)
        require(start in 0..source.content.size)
        return ResponseStream(source.content, start, source.responseLimits)
    }
}

private data class TransferSource(
    val content: ByteArray,
    val responseLimits: List<Int>,
)

private class ResponseStream(
    private val content: ByteArray,
    start: Int,
    private val responseLimits: List<Int>,
) : InputStream() {
    private var position = start
    private var responseIndex = 0

    override fun read(): Int {
        val singleByte = ByteArray(1)
        return if (read(singleByte, 0, 1) == -1) -1 else singleByte[0].toInt() and 0xff
    }

    override fun read(destination: ByteArray, offset: Int, length: Int): Int {
        Objects.checkFromIndexSize(offset, length, destination.size)
        if (length == 0) return 0
        if (position == content.size) return -1
        val count = minOf(length, content.size - position, responseLimits[responseIndex])
        content.copyInto(destination, offset, position, position + count)
        position += count
        if (responseIndex < responseLimits.lastIndex) responseIndex++
        return count
    }
}
