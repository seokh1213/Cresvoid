sealed interface AttemptOutcome

data object TimedOut : AttemptOutcome

class Execution(
    val order: Int,
    val toolUseId: String,
    val startMs: Long,
    val endMs: Long,
    val outcome: AttemptOutcome,
    val attemptCount: Int,
    val result: ToolResultBlock,
)

class ToolPhaseReport(val results: List<ToolResultBlock>, val executions: List<Execution>) {
    val endMs: Long get() = executions.maxOfOrNull { it.endMs } ?: 0
}

class ToolPhase(private val settings: Settings) {
    fun run(calls: List<ToolCall>): ToolPhaseReport {
        val runs = calls.withIndex().mapNotNull { (order, call) -> (call as? ToolCall.Runnable)?.let { BlockRun(order, it) } }
        val executionsByOrder = VirtualClock(settings, runs).runToEnd().associateBy(Execution::order)
        val results = calls.mapIndexed { order, call ->
            when (call) {
                is ToolCall.Skipped -> call.result
                is ToolCall.Runnable -> executionsByOrder.getValue(order).result
            }
        }
        return ToolPhaseReport(results, executionsByOrder.values.toList())
    }
}
