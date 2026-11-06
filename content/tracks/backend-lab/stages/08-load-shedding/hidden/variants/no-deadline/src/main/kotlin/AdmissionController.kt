import java.util.concurrent.Semaphore

class AdmissionController(private val backend: WorkService) {
    private val workers = Semaphore(16, true)
    private val queue = Semaphore(8)

    fun handle(deadlineNanos: Long, work: () -> Unit): Boolean {
        if (!workers.tryAcquire()) {
            if (!queue.tryAcquire()) return false
            try {
                backend.awaitSlot {
                    workers.acquire()
                    true
                }
            } finally {
                queue.release()
            }
        }
        return try {
            work()
            true
        } finally {
            workers.release()
        }
    }
}
