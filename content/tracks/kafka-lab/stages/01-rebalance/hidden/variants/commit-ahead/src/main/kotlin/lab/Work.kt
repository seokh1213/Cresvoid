package lab

import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.concurrent.atomic.AtomicInteger

private data class Receipt(val attempts: Int, val completed: Boolean, val latencyMillis: Long)

class Work {
    val assignments = AtomicInteger()
    val commitFailures = AtomicInteger()
    val fatalErrors = AtomicInteger()
    private val receipts = mutableMapOf<Int, Receipt>()

    fun process(record: ConsumerRecord<String, String>) {
        val id = record.value().toInt()
        synchronized(this) {
            val previous = receipts[id] ?: Receipt(0, false, 0)
            receipts[id] = previous.copy(attempts = previous.attempts + 1)
        }
        Thread.sleep(700)
        synchronized(this) {
            val previous = receipts.getValue(id)
            if (!previous.completed) {
                receipts[id] = previous.copy(completed = true, latencyMillis = System.currentTimeMillis() - record.timestamp())
            }
        }
    }

    @Synchronized
    fun snapshot(): String = buildString {
        appendLine("assignments=${assignments.get()}")
        appendLine("commitFailures=${commitFailures.get()}")
        appendLine("fatalErrors=${fatalErrors.get()}")
        for ((id, receipt) in receipts.toSortedMap()) {
            appendLine("$id,${receipt.attempts},${if (receipt.completed) 1 else 0},${receipt.latencyMillis}")
        }
    }
}
