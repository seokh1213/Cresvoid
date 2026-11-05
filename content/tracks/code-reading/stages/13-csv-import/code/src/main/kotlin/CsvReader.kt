data class CsvRecord(
    val line: Int,
    val fields: List<String>,
)

class CsvSyntax(val line: Int, val reason: String) : RuntimeException(reason)

class CsvReader(private val text: String) {
    private var offset = 0
    private var line = 1

    fun readAll(): List<CsvRecord> = buildList {
        while (offset < text.length) add(readRecord())
    }

    private fun readRecord(): CsvRecord {
        val startLine = line
        val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        val segment = text.substring(offset, end)
        val physicalLine = if (end < text.length) segment.removeSuffix("\r") else segment
        if ('"' !in physicalLine && '\r' !in physicalLine) {
            offset = if (end < text.length) end + 1 else end
            if (end < text.length) line++
            return CsvRecord(startLine, physicalLine.split(","))
        }
        val fields = mutableListOf<String>()
        while (true) {
            fields.add(readField())
            when (peek()) {
                ',' -> take()
                '\r', '\n', null -> {
                    finishRecord()
                    return CsvRecord(startLine, fields.toList())
                }
                else -> throw CsvSyntax(line, "AFTER_QUOTE")
            }
        }
    }

    private fun readField(): String {
        if (peek() == '"') return readQuotedField()
        val field = StringBuilder()
        while (true) {
            when (val character = peek()) {
                ',', '\r', '\n', null -> return field.toString()
                '"' -> throw CsvSyntax(line, "QUOTE_IN_FIELD")
                else -> {
                    field.append(character)
                    take()
                }
            }
        }
    }

    private fun readQuotedField(): String {
        take()
        val field = StringBuilder()
        while (true) {
            when (val character = take()) {
                null -> throw CsvSyntax(line, "UNCLOSED_QUOTE")
                '"' -> {
                    if (peek() != '"') return field.toString()
                    take()
                    field.append('"')
                }
                else -> field.append(character)
            }
        }
    }

    private fun finishRecord() {
        if (peek() == '\r') {
            take()
            if (peek() != '\n') throw CsvSyntax(line, "BARE_CR")
        }
        if (peek() == '\n') take()
    }

    private fun peek(): Char? = text.getOrNull(offset)

    private fun take(): Char? {
        val character = peek() ?: return null
        offset++
        if (character == '\n') line++
        return character
    }
}
