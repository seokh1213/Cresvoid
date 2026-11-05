sealed interface Command {
    data class PutDocument(val document: Document) : Command
    data class DeleteDocument(val id: Int) : Command
    data class Configure(val config: ChunkConfig) : Command
    data object Build : Command
    data object ListChunks : Command
    data class PutVector(val id: ChunkId, val vector: DoubleArray) : Command
    data class LexicalSearch(val k: Int, val text: String) : Command
    data class VectorSearch(val k: Int, val vector: DoubleArray) : Command
}

class CommandParser(private val fields: List<String>) {
    private fun count(expected: Int) = Arguments.count(fields, expected)
    private fun id(position: Int): Int = Arguments.integer(fields[position], 1..1_000_000_000)
    private fun k(position: Int): Int = Arguments.integer(fields[position], 1..1000)

    fun parse(): Command = when (fields[0]) {
        "DOC" -> { count(4); Command.PutDocument(Document(id(1), normalize(fields[2]), normalize(fields[3]))) }
        "DEL" -> { count(2); Command.DeleteDocument(id(1)) }
        "CONFIG" -> { count(5); Command.Configure(Arguments.config(fields.drop(1))) }
        "BUILD" -> { count(1); Command.Build }
        "CHUNKS" -> { count(1); Command.ListChunks }
        "VEC" -> { count(3); Command.PutVector(Arguments.chunkId(fields[1]), Arguments.vector(fields[2])) }
        "BM25" -> { count(3); Command.LexicalSearch(k(1), fields[2]) }
        "VECTOR" -> { count(3); Command.VectorSearch(k(1), Arguments.vector(fields[2])) }
        else -> fail("UNKNOWN_COMMAND")
    }
}
