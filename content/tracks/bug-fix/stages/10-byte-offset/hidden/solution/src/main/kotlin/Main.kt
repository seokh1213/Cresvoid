fun main() {
    CatalogFileStore().use { files ->
        val commands = ExportCommands(files)
        System.`in`.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            println(commands.execute(line))
        }
    }
}

class ExportCommands(private val files: CatalogFileStore) {
    private val planner = RecordRangePlanner(files)
    private val reader = RecordRangeReader(files)

    fun execute(line: String): String {
        val command = line.substringBefore(' ')
        val arguments = line.substringAfter(' ')
        return when (command) {
            "FILE" -> save(arguments)
            "SIZE" -> files.size(arguments).toString()
            "SPLIT" -> split(arguments)
            "READ" -> read(arguments)
            "REPLAY" -> replay(arguments)
            else -> error("Unknown export command")
        }
    }

    private fun save(arguments: String): String {
        val id = arguments.substringBefore(' ')
        val content = arguments.substringAfter(' ', "")
        val size = files.save(id, unescape(content))
        return "STORED $id $size"
    }

    private fun split(arguments: String): String {
        val (id, batchSize) = arguments.split(' ')
        val ranges = planner.plan(id, batchSize.toInt())
        if (ranges.isEmpty()) return "(none)"
        return ranges.joinToString(" ") { "${it.start}:${it.end}" }
    }

    private fun read(arguments: String): String {
        val (id, start, end) = arguments.split(' ')
        return recordsJson(reader.read(id, RecordRange(start.toLong(), end.toLong())))
    }

    private fun replay(arguments: String): String {
        val (id, batchSize) = arguments.split(' ')
        return planner.plan(id, batchSize.toInt()).joinToString(",", "[", "]") { range ->
            recordsJson(reader.read(id, range))
        }
    }

    private fun unescape(encoded: String): String = buildString {
        var cursor = 0
        while (cursor < encoded.length) {
            val character = encoded[cursor++]
            if (character != '\\') {
                append(character)
                continue
            }
            append(when (encoded[cursor++]) {
                'n' -> '\n'
                'r' -> '\r'
                '\\' -> '\\'
                else -> error("Unknown file escape")
            })
        }
    }

    private fun recordsJson(records: List<String>): String =
        records.joinToString(",", "[", "]") { record ->
            buildString {
                append('"')
                for (character in record) {
                    if (character == '"' || character == '\\') append('\\')
                    if (character == '\r') {
                        append("\\r")
                    } else {
                        append(character)
                    }
                }
                append('"')
            }
        }
}
