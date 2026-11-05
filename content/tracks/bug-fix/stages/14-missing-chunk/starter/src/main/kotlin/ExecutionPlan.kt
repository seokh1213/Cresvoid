data class ExecutionId(
    val batchId: String,
    val generation: Int,
    val inputId: String,
)

data class ExecutionPlan(
    val execution: ExecutionId,
    val expectedChunks: Set<String>,
)

class PlanRegistry {
    private val currentPlans = mutableMapOf<String, ExecutionPlan>()

    fun register(execution: ExecutionId, chunkIds: List<String>): ExecutionPlan {
        val plan = ExecutionPlan(execution, chunkIds.toSet())
        currentPlans[execution.batchId] = plan
        return plan
    }

    fun current(batchId: String): ExecutionPlan? = currentPlans[batchId]
}

fun parseExecution(tokens: List<String>): ExecutionId = ExecutionId(
    batchId = tokens[1],
    generation = tokens[2].toInt(),
    inputId = tokens[3],
)

fun parseChunkIds(token: String): List<String> =
    if (token == "-") emptyList() else token.split(',')
