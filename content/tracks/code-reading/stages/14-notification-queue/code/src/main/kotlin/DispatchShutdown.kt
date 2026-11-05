import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReceiptExport(val batchId: String, private val journal: DeliveryJournal) : Runnable {
    override fun run() {
        journal.delivered("receipt:$batchId")
    }
}

class DispatchGroup(queue: NotificationQueue, gateway: DeliveryGate, workerCount: Int) : AutoCloseable {
    val journal = DeliveryJournal()
    val executor: ExecutorService = Executors.newFixedThreadPool(workerCount)

    init {
        repeat(workerCount) {
            executor.execute(DispatchWorker(queue, gateway, journal))
        }
    }

    fun finish(): Boolean = executor.awaitTermination(5, TimeUnit.SECONDS).also {
        check(it) { "Dispatch workers did not terminate" }
    }

    fun abort(): List<String> = executor.shutdownNow()
        .map { (it as ReceiptExport).batchId }

    override fun close() {
        executor.shutdownNow()
        finish()
    }
}
