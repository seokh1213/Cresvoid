import java.util.concurrent.ArrayBlockingQueue

sealed interface DispatchEntry {
    data class Notification(val id: String) : DispatchEntry
    data object Stop : DispatchEntry
}

class NotificationQueue(capacity: Int) {
    private val entries = ArrayBlockingQueue<DispatchEntry>(capacity)
    var dropped: Int = 0
        private set

    fun offer(id: String): Boolean {
        val accepted = entries.offer(DispatchEntry.Notification(id))
        if (!accepted) dropped++
        return accepted
    }

    fun put(id: String) {
        entries.put(DispatchEntry.Notification(id))
    }

    fun enqueueStops(count: Int) {
        repeat(count) { entries.put(DispatchEntry.Stop) }
    }

    fun take(): DispatchEntry = entries.take()

    fun pending(): List<String> = entries.toList()
        .filterIsInstance<DispatchEntry.Notification>()
        .map { it.id }
}

fun List<String>.display(): String = if (isEmpty()) "-" else joinToString(",")
