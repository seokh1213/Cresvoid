const val BLOCK_END = "END"

private const val VALUE_LIMIT = 1_000_000_000L
private val ROW_BLOCK_COMMANDS = setOf("DATA", "APPEND")

fun opensRowBlock(command: String): Boolean = command.substringBefore(' ') in ROW_BLOCK_COMMANDS

sealed interface RowBlock {
    fun accept(rowLine: String)

    fun finish(): String?
}

class RejectedBlock(private val failure: CommandFailure) : RowBlock {
    override fun accept(rowLine: String) {}

    override fun finish(): String = failure.line
}

class RowLoader(
    private val dataset: Dataset,
    private val dictionary: KeyDictionary,
    private val onLoaded: () -> Unit,
) : RowBlock {
    private val mark = dataset.mark()
    private var rowCount = 0
    private var failedRow: Int? = null

    override fun accept(rowLine: String) {
        rowCount++
        if (failedRow != null) return
        if (!loadRow(rowLine)) {
            failedRow = rowCount
            dataset.rollback(mark)
        }
    }

    override fun finish(): String? {
        val failed = failedRow
        if (failed != null) return CommandFailure(ErrorCode.ROW, failed).line
        onLoaded()
        return null
    }

    private fun loadRow(line: String): Boolean {
        val partitionEnd = line.indexOf(' ')
        if (partitionEnd < 0) return false
        val keyStart = skipSpaces(line, partitionEnd)
        val keyEnd = line.indexOf(' ', keyStart)
        if (keyEnd < 0) return false
        val valueStart = skipSpaces(line, keyEnd)
        if (line.indexOf(' ', valueStart) >= 0) return false

        val partition = parseInteger(line, 0, partitionEnd)?.takeIf { it in 0 until dataset.partitionCount } ?: return false
        val key = when {
            keyEnd - keyStart == NULL_TOKEN.length && line.startsWith(NULL_TOKEN, keyStart) -> NULL_KEY
            isKey(line, keyStart, keyEnd) -> dictionary.keyOf(line.substring(keyStart, keyEnd))
            else -> return false
        }
        val value = parseInteger(line, valueStart, line.length)?.takeIf { it in -VALUE_LIMIT..VALUE_LIMIT } ?: return false
        dataset.add(partition.toInt(), key, value)
        return true
    }

    private fun skipSpaces(line: String, from: Int): Int {
        var index = from
        while (line[index] == ' ') index++
        return index
    }
}
