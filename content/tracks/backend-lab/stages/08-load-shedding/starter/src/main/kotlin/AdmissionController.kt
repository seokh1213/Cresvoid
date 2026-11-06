import java.util.concurrent.Semaphore

class AdmissionController(private val backend: WorkService) {
    private val workers = Semaphore(16)

    fun handle(deadlineNanos: Long, work: () -> Unit): Boolean {
        backend.awaitSlot {
            workers.acquire()
            true
        }
        return try {
            work()
            true
        } finally {
            workers.release()
        }
    }
}
