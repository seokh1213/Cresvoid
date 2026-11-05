sealed interface Command {
    data class PutDocument(val document: Document) : Command
    data class DeleteDocument(val id: Int) : Command
    data class Configure(val config: ChunkConfig) : Command
    data object Build : Command
    data object ListChunks : Command
}

class CommandParser(private val fields: List<String>) {
    private fun count(expected: Int) = Arguments.count(fields, expected)
    private fun id(position: Int): Int = Arguments.integer(fields[position], 1..1_000_000_000)

    fun parse(): Command = when (fields[0]) {
        "DOC" -> { count(4); Command.PutDocument(Document(id(1), normalize(fields[2]), normalize(fields[3]))) }
        "DEL" -> { count(2); Command.DeleteDocument(id(1)) }
        "CONFIG" -> { count(5); Command.Configure(Arguments.config(fields.drop(1))) }
        "BUILD" -> { count(1); Command.Build }
        "CHUNKS" -> { count(1); Command.ListChunks }
        else -> fail("UNKNOWN_COMMAND")
    }
}
