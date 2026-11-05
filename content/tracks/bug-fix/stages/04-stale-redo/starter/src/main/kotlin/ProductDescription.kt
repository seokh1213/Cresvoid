data class DescriptionCheckpoint(val text: String, val cursor: Int)

class ProductDescription {
    private val content = StringBuilder()

    var cursor: Int = 0
        private set

    val text: String
        get() = content.toString()

    val length: Int
        get() = content.codePointCount(0, content.length)

    fun containsPosition(position: Int): Boolean = position in 0..length

    fun containsRange(position: Int, count: Int): Boolean =
        containsPosition(position) && count >= 0 && count <= length - position

    fun checkpoint(): DescriptionCheckpoint = DescriptionCheckpoint(text, cursor)

    fun restore(checkpoint: DescriptionCheckpoint) {
        content.setLength(0)
        content.append(checkpoint.text)
        cursor = checkpoint.cursor
    }

    fun moveCursor(position: Int) {
        require(containsPosition(position))
        cursor = position
    }

    fun read(position: Int, count: Int): String {
        require(containsRange(position, count))
        val start = offsetOf(position)
        val end = offsetOf(position + count)
        return content.substring(start, end)
    }

    fun insert(position: Int, text: String) {
        require(containsPosition(position))
        content.insert(offsetOf(position), text)
        cursor = position + text.codePointCount(0, text.length)
    }

    fun remove(position: Int, count: Int) {
        require(containsRange(position, count))
        val start = offsetOf(position)
        val end = offsetOf(position + count)
        content.delete(start, end)
        cursor = position
    }

    private fun offsetOf(position: Int): Int = content.offsetByCodePoints(0, position)
}
