import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException

class BatchCommands : CommandGroup {
    override val commands = setOf("CHANGE", "SOURCE", "PERSIST")
    private val changes = mutableListOf<PlannedChange>()
    private var sourceEnd: SourceEnd? = null

    override fun execute(command: String, args: List<String>): RunOutput? = when (command) {
        "CHANGE" -> { addChange(args); null }
        "SOURCE" -> { setSourceEnd(args); null }
        else -> runBatch(args)
    }

    private fun addChange(args: List<String>) {
        args.requireSize(2, "CHANGE")
        val time = parseDuration(args[0])
        if (changes.isNotEmpty() && time <= changes.last().time) {
            throw ScenarioException("CHANGE 의 시각은 앞 CHANGE 보다 늦어야 한다")
        }
        if (sourceEnd != null) throw ScenarioException("SOURCE 뒤에 CHANGE 를 둘 수 없다")
        val id = parseName(args[1])
        if (changes.any { it.id == id }) throw ScenarioException("이미 있는 변경이다: $id")
        changes += PlannedChange(time, id)
    }

    private fun setSourceEnd(args: List<String>) {
        args.requireSize(2, "SOURCE")
        if (sourceEnd != null) throw ScenarioException("SOURCE 는 한 번만 쓴다")
        val time = parseDuration(args[0])
        if (changes.isNotEmpty() && time <= changes.last().time) {
            throw ScenarioException("SOURCE 는 마지막 CHANGE 보다 늦어야 한다")
        }
        val failure = when (args[1]) {
            "END" -> false
            "FAIL" -> true
            else -> throw ScenarioException("SOURCE 의 끝은 END 나 FAIL 이다")
        }
        sourceEnd = SourceEnd(time, failure)
    }

    private fun runBatch(args: List<String>): RunOutput {
        args.requireSize(6, "PERSIST")
        val end = sourceEnd ?: throw ScenarioException("PERSIST 앞에 SOURCE 가 없다")
        val policy = BatchPolicy(
            size = parseNumber(args[0], 1L..100_000L).toInt(),
            window = parseDuration(args[1]),
            waiting = parseNumber(args[2], 0L..100_000L).toInt(),
        )
        val saveDelay = parseDuration(args[3])
        val failOn = parseNumber(args[4], 0L..100_000L).toInt()
        val cancelAt = parseNumber(args[5]).takeIf { it > 0 }
        val planned = changes.toList()
        changes.clear()
        sourceEnd = null
        return VirtualTime.simulate {
            val log = EventLog(::now)
            val services = FakeBatchServices(log, ::now, saveDelay, failOn)
            val ending = runToEnd(cancelAt) {
                try {
                    BatchOutcome.Completed(batchSource(planned, end, log).persistBatches(policy, services))
                } catch (failure: SourceFailure) {
                    BatchOutcome.SourceFailed
                } catch (failure: SaveFailure) {
                    BatchOutcome.SaveFailed(failure.ordinal)
                }
            }
            RunOutput(log.lines(), ending.toBatchLine())
        }
    }
}

private data class PlannedChange(val time: Long, val id: String)

private data class SourceEnd(val time: Long, val failure: Boolean)

private fun VirtualTime.batchSource(changes: List<PlannedChange>, end: SourceEnd, log: EventLog): Flow<BatchChange> = flow {
    for (change in changes) {
        delay((change.time - now()).coerceAtLeast(0))
        yield()
        log.record("offer ${change.id}")
        emit(BatchChange(change.id))
    }
    delay((end.time - now()).coerceAtLeast(0))
    yield()
    if (end.failure) {
        log.record("source fail")
        throw SourceFailure()
    }
    log.record("source end")
}

private class FakeBatchServices(
    private val log: EventLog,
    private val clock: () -> Long,
    private val delayMillis: Long,
    private val failOn: Int,
) : BatchServices {
    private var ordinal = 0

    override fun now(): Long = clock()

    override fun take(change: BatchChange) = log.record("take ${change.id}")

    override fun seal(batch: List<BatchChange>) = log.record("seal ${batch.ids()}")

    override suspend fun save(batch: List<BatchChange>) {
        ordinal++
        val label = "$ordinal ${batch.ids()}"
        log.record("start save $label")
        try {
            delay(delayMillis)
        } catch (cancelled: CancellationException) {
            log.record("cancel save $label")
            throw cancelled
        }
        if (ordinal == failOn) {
            log.record("fail save $label")
            throw SaveFailure(ordinal)
        }
        log.record("ok save $label")
    }
}

private fun List<BatchChange>.ids(): String = joinToString(",") { it.id }

private sealed interface BatchOutcome {
    data class Completed(val report: BatchReport) : BatchOutcome

    data object SourceFailed : BatchOutcome

    data class SaveFailed(val ordinal: Int) : BatchOutcome
}

private fun Ending<BatchOutcome>.toBatchLine(): String = when (this) {
    is Ending.Returned -> when (val outcome = value) {
        is BatchOutcome.Completed -> "PERSIST DONE $time saved=${outcome.report.saved} changes=${outcome.report.changes}"
        BatchOutcome.SourceFailed -> "PERSIST FAIL SOURCE $time"
        is BatchOutcome.SaveFailed -> "PERSIST FAIL SAVE $time ${outcome.ordinal}"
    }
    is Ending.Cancelled -> "PERSIST CANCELLED $time"
    is Ending.Stuck -> "PERSIST STUCK $time"
}
