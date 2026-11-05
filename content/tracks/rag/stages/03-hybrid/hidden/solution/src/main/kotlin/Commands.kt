sealed interface Command {
    data class PutDocument(val document: Document) : Command
    data class DeleteDocument(val id: Int) : Command
    data class Configure(val config: ChunkConfig) : Command
    data object Build : Command
    data object ListChunks : Command
    data class PutVector(val id: ChunkId, val vector: DoubleArray) : Command
    data class LexicalSearch(val k: Int, val text: String) : Command
    data class VectorSearch(val k: Int, val vector: DoubleArray) : Command
    data class ReciprocalFusion(val k: Int, val query: FusionQuery) : Command
    data class MarginalRelevance(val k: Int, val query: FusionQuery, val lambda: Double) : Command
    data class Group(val k: Int, val query: FusionQuery) : Command
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
        "RRF" -> { count(6); Command.ReciprocalFusion(k(1), fusion(fields)) }
        "MMR" -> {
            count(7)
            val lambda = Arguments.real(fields[4])
            if (lambda < 0.0 || lambda > 1.0) fail("BAD_ARGUMENT")
            Command.MarginalRelevance(k(1), fusion(fields.take(4) + fields.drop(5)), lambda)
        }
        "GROUP" -> { count(6); Command.Group(k(1), fusion(fields)) }
        else -> fail("UNKNOWN_COMMAND")
    }

    private fun fusion(parts: List<String>): FusionQuery {
        val constant = if (parts[3] == "-") 60 else Arguments.integer(parts[3], 0..1000)
        return FusionQuery(Arguments.integer(parts[2], 1..1000), constant, parts[4], Arguments.vector(parts[5]))
    }
}
