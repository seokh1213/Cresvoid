import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun main() {
    generateSequence(::readlnOrNull).filter { it.isNotBlank() }.forEach { command ->
        val report = when (command.trim()) {
            "BURST" -> burst()
            "ABORT" -> abortBatch()
            "SHORTAGE" -> stopShortage()
            "TAIL" -> lateArrival()
            "GRACEFUL" -> gracefulShutdown()
            else -> error("Unknown dispatch scenario")
        }
        println(report)
    }
}

fun burst(): String {
    val queue = NotificationQueue(2)
    val gateway = DeliveryGate()
    return DispatchGroup(queue, gateway, 1).use { group ->
        queue.put("A")
        gateway.entered.awaitSignal()
        listOf("B", "C", "D").forEach { queue.offer(it) }
        val producerReady = CountDownLatch(1)
        val producerDone = CountDownLatch(1)
        val producer = Thread {
            producerReady.countDown()
            queue.put("E")
            producerDone.countDown()
        }
        producer.start()
        producerReady.awaitSignal()
        val waiting = producerDone.count == 1L
        gateway.open()
        producerDone.awaitSignal()
        producer.join()
        queue.enqueueStops(1)
        group.executor.shutdown()
        group.finish()
        "dropped=${queue.dropped} waiting=$waiting " +
            "sent=${group.journal.sentIds().display()} pending=${queue.pending().display()}"
    }
}

fun abortBatch(): String {
    val queue = NotificationQueue(2)
    val gateway = DeliveryGate()
    return DispatchGroup(queue, gateway, 1).use { group ->
        queue.put("A")
        gateway.entered.awaitSignal()
        queue.put("B")
        group.executor.execute(ReceiptExport("daily", group.journal))
        val returned = group.abort()
        val terminated = group.finish()
        "returned=${returned.display()} sent=${group.journal.sentIds().display()} " +
            "interrupted=${group.journal.interruptedIds().display()} " +
            "pending=${queue.pending().display()} terminated=$terminated"
    }
}

fun stopShortage(): String {
    val queue = NotificationQueue(2)
    val gateway = DeliveryGate()
    return DispatchGroup(queue, gateway, 2).use { group ->
        queue.put("A")
        gateway.entered.awaitSignal()
        queue.enqueueStops(1)
        group.journal.firstStop.awaitSignal()
        queue.put("B")
        group.executor.shutdown()
        val terminated = group.executor.awaitTermination(0, TimeUnit.MILLISECONDS)
        "stopped=${group.journal.stoppedCount()} terminated=$terminated " +
            "pending=${queue.pending().display()}"
    }
}

fun lateArrival(): String {
    val queue = NotificationQueue(4)
    queue.put("A")
    queue.enqueueStops(1)
    queue.put("B")
    val gateway = DeliveryGate()
    gateway.open()
    return DispatchGroup(queue, gateway, 1).use { group ->
        group.executor.shutdown()
        group.finish()
        "sent=${group.journal.sentIds().display()} " +
            "stopped=${group.journal.stoppedCount()} pending=${queue.pending().display()}"
    }
}

fun gracefulShutdown(): String {
    val queue = NotificationQueue(2)
    val gateway = DeliveryGate("B")
    return DispatchGroup(queue, gateway, 1).use { group ->
        queue.put("A")
        queue.put("B")
        gateway.entered.awaitSignal()
        queue.enqueueStops(1)
        queue.put("C")
        group.executor.execute(ReceiptExport("daily", group.journal))
        group.executor.shutdown()
        val before = group.executor.awaitTermination(0, TimeUnit.MILLISECONDS)
        gateway.open()
        val after = group.finish()
        "before=$before after=$after sent=${group.journal.sentIds().display()} " +
            "interrupted=${group.journal.interruptedIds().display()} " +
            "stopped=${group.journal.stoppedCount()} pending=${queue.pending().display()}"
    }
}
