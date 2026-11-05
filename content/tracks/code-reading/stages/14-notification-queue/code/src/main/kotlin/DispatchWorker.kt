import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeliveryGate(private val pausedId: String? = null) {
    val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)

    fun send(notification: DispatchEntry.Notification) {
        require(notification.id.isNotBlank())
        if (pausedId != null && notification.id != pausedId) return
        entered.countDown()
        release.await()
    }

    fun open() {
        release.countDown()
    }
}

class DeliveryJournal {
    private val sent = mutableListOf<String>()
    private val interrupted = mutableListOf<String>()
    private var stops = 0
    val firstStop = CountDownLatch(1)

    @Synchronized
    fun delivered(id: String) {
        sent += id
    }

    @Synchronized
    fun interrupted(id: String) {
        interrupted += id
    }

    @Synchronized
    fun stopped() {
        stops++
        firstStop.countDown()
    }

    @Synchronized
    fun sentIds(): List<String> = sent.toList()

    @Synchronized
    fun interruptedIds(): List<String> = interrupted.toList()

    @Synchronized
    fun stoppedCount(): Int = stops
}

class DispatchWorker(
    private val queue: NotificationQueue,
    private val gateway: DeliveryGate,
    private val journal: DeliveryJournal,
) : Runnable {
    override fun run() {
        var sending: DispatchEntry.Notification? = null
        try {
            while (true) {
                when (val entry = queue.take()) {
                    DispatchEntry.Stop -> {
                        journal.stopped()
                        return
                    }
                    is DispatchEntry.Notification -> {
                        sending = entry
                        gateway.send(entry)
                        journal.delivered(entry.id)
                        sending = null
                    }
                }
            }
        } catch (_: InterruptedException) {
            journal.interrupted(sending?.id ?: "idle")
            Thread.currentThread().interrupt()
        }
    }
}

fun CountDownLatch.awaitSignal() {
    check(await(5, TimeUnit.SECONDS)) { "Dispatch coordination timed out" }
}
