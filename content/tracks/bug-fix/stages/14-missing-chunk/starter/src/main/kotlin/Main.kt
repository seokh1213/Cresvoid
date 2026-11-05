class BatchCommands(
    private val plans: PlanRegistry,
    private val results: ChunkResultRepository,
    private val completion: BatchCompletion,
) {
    fun execute(line: String): String {
        val tokens = line.trim().split(Regex("\\s+"))
        return when (tokens.first()) {
            "PLAN" -> registerPlan(tokens)
            "RESULT" -> recordResult(tokens)
            "CHECK" -> checkBatch(tokens)
            "INSPECT" -> inspectChunk(tokens)
            else -> error("Unknown command")
        }
    }

    private fun registerPlan(tokens: List<String>): String {
        require(tokens.size == 5)
        val execution = parseExecution(tokens)
        val chunkIds = parseChunkIds(tokens[4])
        val plan = plans.register(execution, chunkIds)
        return "PLANNED ${execution.batchId} ${plan.expectedChunks.size}"
    }

    private fun recordResult(tokens: List<String>): String {
        require(tokens.size == 7)
        val execution = parseExecution(tokens)
        val result = parseChunkResult(tokens)
        results.record(execution, result)
        return "RECORDED ${execution.batchId} ${result.chunkId}"
    }

    private fun checkBatch(tokens: List<String>): String {
        require(tokens.size == 2)
        val batchId = tokens[1]
        val state = completion.check(batchId)
        return "$batchId ${state.name}"
    }

    private fun inspectChunk(tokens: List<String>): String {
        require(tokens.size == 3)
        return completion.inspect(tokens[1], tokens[2])
    }
}

fun main() {
    val plans = PlanRegistry()
    val results = ChunkResultRepository()
    val completion = BatchCompletion(plans, results)
    val commands = BatchCommands(plans, results, completion)
    val output = System.out.bufferedWriter()

    System.`in`.bufferedReader().use { input ->
        input.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                output.write(commands.execute(line))
                output.newLine()
            }
    }
    output.flush()
}
