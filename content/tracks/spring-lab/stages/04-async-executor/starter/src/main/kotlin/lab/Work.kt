package lab

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.scheduling.annotation.Async

class Job(val id: Int, val submitted: Long) {
    @Volatile var started = -1L
    @Volatile var finished = -1L
    @Volatile var thread = "pending"
}

class WorkState {
    val origin = System.nanoTime()
    val gate = CountDownLatch(1)
    val jobs = ConcurrentHashMap<Int, Job>()
    val running = AtomicInteger()
    val peak = AtomicInteger()
    val completed = AtomicInteger()
    val failed = AtomicInteger()
    fun millis(): Long = (System.nanoTime() - origin) / 1_000_000
}

open class Worker(private val state: WorkState) {
    @Async("taskExecutor")
    open fun execute(job: Job) {
        val active = state.running.incrementAndGet()
        state.peak.accumulateAndGet(active, ::maxOf)
        job.thread = Thread.currentThread().name
        job.started = state.millis()
        try {
            state.gate.await()
            Thread.sleep(1000)
            job.finished = state.millis()
            state.completed.incrementAndGet()
        } catch (interrupted: InterruptedException) {
            state.failed.incrementAndGet()
            Thread.currentThread().interrupt()
        } finally {
            state.running.decrementAndGet()
        }
    }
}
