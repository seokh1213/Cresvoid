import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class AdmissionController(private val backend: WorkService) {
    private val workers = Semaphore(16, true)
    private val queue = Semaphore(8)

    fun handle(deadlineNanos: Long, work: () -> Unit): Boolean {
        if (System.nanoTime() >= deadlineNanos) return false
        if (workers.tryAcquire()) return runWithPermit(deadlineNanos, work)
        if (!queue.tryAcquire()) return false
        val acquired = try {
            backend.awaitSlot {
                val remaining = (deadlineNanos - System.nanoTime()).coerceAtLeast(0)
                workers.tryAcquire(remaining, TimeUnit.NANOSECONDS)
            }
        } finally {
            queue.release()
        }
        if (!acquired) return false
        return runWithPermit(deadlineNanos, work)
    }

    private fun runWithPermit(deadlineNanos: Long, work: () -> Unit): Boolean = try {
        if (System.nanoTime() >= deadlineNanos) {
            false
        } else {
            work()
            true
        }
    } finally {
        workers.release()
    }
}
