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
    data class PutQuery(val query: EvaluationQuery) : Command
    data class Judge(val qid: Int, val document: Int, val grade: Int) : Command
    data class Evaluate(val qid: Int, val request: EvaluationRequest) : Command
    data class EvaluateAll(val request: EvaluationRequest) : Command
    data class Compare(val k: Int, val first: ChunkConfig, val second: ChunkConfig) : Command
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
        "QRY" -> { count(4); Command.PutQuery(EvaluationQuery(id(1), fields[2], Arguments.vector(fields[3]))) }
        "REL" -> { count(4); Command.Judge(id(1), id(2), Arguments.integer(fields[3], 0..3)) }
        "EVAL" -> { count(4); Command.Evaluate(id(1), EvaluationRequest(Arguments.choice(fields[2]), k(3))) }
        "EVALALL" -> { count(3); Command.EvaluateAll(EvaluationRequest(Arguments.choice(fields[1]), k(2))) }
        "COMPARE" -> {
            count(10)
            Command.Compare(k(1), Arguments.config(fields.subList(2, 6)), Arguments.config(fields.subList(6, 10)))
        }
        else -> fail("UNKNOWN_COMMAND")
    }

    private fun fusion(parts: List<String>): FusionQuery {
        val constant = if (parts[3] == "-") 60 else Arguments.integer(parts[3], 0..1000)
        return FusionQuery(Arguments.integer(parts[2], 1..1000), constant, parts[4], Arguments.vector(parts[5]))
    }
}
