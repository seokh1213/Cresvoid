enum class BatchState {
    UNPLANNED,
    PENDING,
    COMPLETE,
}

class BatchCompletion(
    private val plans: PlanRegistry,
    private val results: ChunkResultRepository,
) {
    fun check(batchId: String): BatchState {
        val plan = plans.current(batchId) ?: return BatchState.UNPLANNED
        val executionResults = results.forExecution(plan.execution)
        val expectedResults = executionResults.filterKeys { it in plan.expectedChunks }
        val complete = expectedResults.values.all { result ->
            result.status == ChunkStatus.SUCCESS
        }
        return if (complete) BatchState.COMPLETE else BatchState.PENDING
    }

    fun inspect(batchId: String, chunkId: String): String {
        val plan = plans.current(batchId) ?: return "$batchId UNPLANNED"
        val result = results.find(plan.execution, chunkId)
            ?: return "$batchId $chunkId MISSING"
        return "$batchId ${result.render()}"
    }
}
