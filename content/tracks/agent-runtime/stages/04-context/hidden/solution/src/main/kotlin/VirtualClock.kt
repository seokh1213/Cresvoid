import java.util.PriorityQueue

class BlockRun(val order: Int, val call: ToolCall.Runnable) {
    var startMs = 0L
        private set
    var attemptCount = 0
        private set

    fun startNextAttempt(nowMs: Long): Attempt {
        if (attemptCount == 0) startMs = nowMs
        val attempt = call.attempts[minOf(attemptCount, call.attempts.lastIndex)]
        attemptCount++
        return attempt
    }
}

private sealed interface ClockEvent {
    val atMs: Long
    val run: BlockRun

    class AttemptEnded(override val atMs: Long, override val run: BlockRun, val outcome: AttemptOutcome) : ClockEvent

    class BackoffEnded(override val atMs: Long, override val run: BlockRun) : ClockEvent
}

class VirtualClock(private val settings: Settings, runs: List<BlockRun>) {
    private val ready = PriorityQueue(compareBy(BlockRun::order)).apply { addAll(runs) }
    private val upcoming = PriorityQueue(compareBy(ClockEvent::atMs))
    private val executions = mutableListOf<Execution>()
    private var freeSlots = settings.concurrency
    private var nowMs = 0L

    fun runToEnd(): List<Execution> {
        while (true) {
            startReadyRuns()
            nowMs = upcoming.peek()?.atMs ?: return executions
            // 같은 시각의 일을 모두 반영한 뒤에 슬롯을 배정해야 다시 준비된 블록이 순서대로 슬롯을 받는다
            while (upcoming.peek()?.atMs == nowMs) handle(upcoming.remove())
        }
    }

    private fun startReadyRuns() {
        while (freeSlots > 0 && ready.isNotEmpty()) {
            upcoming += startAttempt(ready.remove())
            freeSlots--
        }
    }

    private fun startAttempt(run: BlockRun): ClockEvent.AttemptEnded {
        val attempt = run.startNextAttempt(nowMs)
        val timedOut = settings.timeoutMs > 0 && attempt.durationMs > settings.timeoutMs
        val durationMs = if (timedOut) settings.timeoutMs else attempt.durationMs
        return ClockEvent.AttemptEnded(nowMs + durationMs, run, if (timedOut) TimedOut else attempt.result)
    }

    private fun handle(event: ClockEvent) {
        when (event) {
            is ClockEvent.AttemptEnded -> {
                freeSlots++
                afterAttempt(event.run, event.outcome)
            }
            is ClockEvent.BackoffEnded -> ready += event.run
        }
    }

    private fun afterAttempt(run: BlockRun, outcome: AttemptOutcome) {
        val retry = outcome !is FakeResult.Output && !run.call.sideEffect && run.attemptCount <= settings.retries
        if (!retry) {
            executions += Execution(run.order, run.call.use.id, run.startMs, nowMs, outcome, run.attemptCount, resultOf(run.call, outcome))
            return
        }
        val waitMs = settings.backoffMs * (1L shl (run.attemptCount - 1))
        if (waitMs == 0L) ready += run else upcoming += ClockEvent.BackoffEnded(nowMs + waitMs, run)
    }

    private fun resultOf(call: ToolCall.Runnable, outcome: AttemptOutcome): ToolResultBlock = when (outcome) {
        is FakeResult.Output -> ToolResultBlock(call.use.id, isError = false, content = outcome.text)
        is FakeResult.Failure -> ToolResultBlock(call.use.id, isError = true, content = outcome.message)
        TimedOut -> ToolResultBlock(call.use.id, isError = true, content = timeoutMessage(call.sideEffect))
    }

    private fun timeoutMessage(sideEffect: Boolean): String =
        if (sideEffect) "timeout after ${settings.timeoutMs} ms; outcome unknown" else "timeout after ${settings.timeoutMs} ms"
}
