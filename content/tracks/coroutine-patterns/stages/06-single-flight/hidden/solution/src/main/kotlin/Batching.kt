import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

data class BatchChange(val id: String)

data class BatchPolicy(val size: Int, val window: Long, val waiting: Int)

interface BatchServices {
    fun now(): Long

    fun take(change: BatchChange)

    fun seal(batch: List<BatchChange>)

    suspend fun save(batch: List<BatchChange>)
}

class SourceFailure : RuntimeException("원본이 실패했다")

class SaveFailure(val ordinal: Int) : RuntimeException("$ordinal 번째 저장이 실패했다")

data class BatchReport(val saved: Int, val changes: Int)

suspend fun Flow<BatchChange>.persistBatches(
    policy: BatchPolicy,
    services: BatchServices,
): BatchReport = coroutineScope {
    val arrivals = Channel<BatchChange>(Channel.RENDEZVOUS)
    val ready = Channel<List<BatchChange>>(policy.waiting)
    var saved = 0
    var changes = 0
    val sourceJob = launch {
        try {
            this@persistBatches.collect { arrivals.send(it) }
        } catch (caught: Throwable) {
            arrivals.close(caught)
            throw caught
        } finally {
            arrivals.close()
        }
    }
    val saveJob = launch {
        for (batch in ready) {
            services.save(batch)
            saved++
            changes += batch.size
        }
    }
    try {
        collectBatches(arrivals, ready, policy, services)
        ready.close()
        saveJob.join()
        BatchReport(saved, changes)
    } finally {
        sourceJob.cancel()
        ready.cancel()
    }
}

private suspend fun collectBatches(
    arrivals: Channel<BatchChange>,
    ready: Channel<List<BatchChange>>,
    policy: BatchPolicy,
    services: BatchServices,
) = coroutineScope {
    var batch = ArrayList<BatchChange>()
    var deadline = Long.MAX_VALUE
    var timer: Deferred<Unit>? = null
    suspend fun seal() {
        if (batch.isEmpty()) return
        timer?.cancel()
        timer = null
        val closed = batch.toList()
        services.seal(closed)
        ready.send(closed)
        batch = ArrayList()
    }
    while (true) {
        val next = if (batch.isEmpty()) {
            BatchNext.Received(arrivals.receiveCatching())
        } else {
            select<BatchNext> {
                checkNotNull(timer).onAwait { BatchNext.TimedOut }
                arrivals.onReceiveCatching { BatchNext.Received(it) }
            }
        }
        if (next == BatchNext.TimedOut) {
            seal()
            continue
        }
        val received = (next as BatchNext.Received).result
        if (batch.isNotEmpty() && services.now() >= deadline) seal()
        if (received.isClosed) {
            received.exceptionOrNull()?.let { throw it }
            seal()
            return@coroutineScope
        }
        val change = received.getOrThrow()
        services.take(change)
        if (batch.isEmpty()) {
            deadline = services.now() + policy.window
            timer = async { delay(policy.window) }
        }
        batch += change
        if (batch.size == policy.size) seal()
    }
}

private sealed interface BatchNext {
    data object TimedOut : BatchNext

    data class Received(val result: ChannelResult<BatchChange>) : BatchNext
}
