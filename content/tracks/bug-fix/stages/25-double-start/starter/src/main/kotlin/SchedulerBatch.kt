import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private class ReadCheckpoint(requests: Int) {
    private val readers = CountDownLatch(requests)
    private val resume = CountDownLatch(1)

    fun afterRead() {
        readers.countDown()
        check(resume.await(5, TimeUnit.SECONDS)) { "checkpoint was not released" }
    }

    fun releaseReaders() {
        try {
            // Not every request is guaranteed to reach the checkpoint.
            readers.await(1, TimeUnit.SECONDS)
        } finally {
            resume.countDown()
        }
    }
}

data class StartSummary(val accepted: Int, val rejected: Int) {
    override fun toString(): String = "START accepted=$accepted rejected=$rejected"
}

class SchedulerBatch(
    private val state: MemoryRunStateStore,
    private val job: SettlementJob,
) {
    fun start(day: String, requests: Int): StartSummary {
        val checkpoint = ReadCheckpoint(requests)
        val ready = CountDownLatch(requests)
        val launch = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(requests)
        state.afterRead = checkpoint::afterRead
        try {
            val results = (1..requests).map {
                workers.submit(Callable {
                    ready.countDown()
                    check(launch.await(5, TimeUnit.SECONDS))
                    job.start(day)
                })
            }
            check(ready.await(5, TimeUnit.SECONDS))
            launch.countDown()
            checkpoint.releaseReaders()
            val accepted = results.count { it.get(5, TimeUnit.SECONDS) }
            return StartSummary(accepted, requests - accepted)
        } finally {
            launch.countDown()
            workers.shutdownNow()
            check(workers.awaitTermination(5, TimeUnit.SECONDS))
            state.afterRead = {}
        }
    }
}
