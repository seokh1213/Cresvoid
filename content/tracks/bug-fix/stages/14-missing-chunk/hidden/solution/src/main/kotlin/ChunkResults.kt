enum class ChunkStatus {
    SUCCESS,
    FAILURE,
}

data class ChunkResult(
    val chunkId: String,
    val status: ChunkStatus,
    val rowCount: Long,
) {
    fun render(): String = "$chunkId ${status.name} $rowCount"
}

class ChunkResultRepository {
    private val executions = mutableMapOf<ExecutionId, MutableMap<String, ChunkResult>>()

    fun record(execution: ExecutionId, result: ChunkResult) {
        val chunks = executions.getOrPut(execution) { mutableMapOf() }
        chunks[result.chunkId] = result
    }

    fun find(execution: ExecutionId, chunkId: String): ChunkResult? =
        executions[execution]?.get(chunkId)

    fun forExecution(execution: ExecutionId): Map<String, ChunkResult> =
        executions[execution]?.toMap().orEmpty()
}

fun parseChunkResult(tokens: List<String>): ChunkResult = ChunkResult(
    chunkId = tokens[4],
    status = ChunkStatus.valueOf(tokens[5]),
    rowCount = tokens[6].toLong(),
)
