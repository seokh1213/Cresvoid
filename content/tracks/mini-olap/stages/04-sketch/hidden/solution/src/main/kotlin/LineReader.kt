import java.io.ByteArrayOutputStream
import java.io.InputStream

class LineReader(private val input: InputStream) {
    private val buffer = ByteArray(1 shl 16)
    private var position = 0
    private var limit = 0
    private val unfinishedLine = ByteArrayOutputStream()

    fun readLine(): String? {
        while (true) {
            if (position == limit && !fill()) {
                return if (unfinishedLine.size() == 0) null else takeUnfinishedLine()
            }
            val newline = newlineIndex()
            if (newline >= 0) {
                val line = if (unfinishedLine.size() == 0) {
                    String(buffer, position, newline - position, Charsets.UTF_8)
                } else {
                    unfinishedLine.write(buffer, position, newline - position)
                    takeUnfinishedLine()
                }
                position = newline + 1
                return line
            }
            unfinishedLine.write(buffer, position, limit - position)
            position = limit
        }
    }

    private fun fill(): Boolean {
        position = 0
        limit = maxOf(input.read(buffer), 0)
        return limit > 0
    }

    private fun newlineIndex(): Int {
        for (index in position until limit) {
            if (buffer[index] == NEWLINE) return index
        }
        return -1
    }

    private fun takeUnfinishedLine(): String = unfinishedLine.toString(Charsets.UTF_8).also { unfinishedLine.reset() }

    private companion object {
        const val NEWLINE = '\n'.code.toByte()
    }
}
