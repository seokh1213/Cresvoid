import java.util.PriorityQueue

sealed interface ScheduledEvent {
    class OperationArrival(val operation: StoreOperation) : ScheduledEvent

    class ReplyArrival(val query: CounterQuery, val reply: StoreReply) : ScheduledEvent

    class WorkerReturn(val gateway: Gateway) : ScheduledEvent
}

class Scheduler {
    private val queue = PriorityQueue<Entry>()
    private var lastSequence = 0L

    fun schedule(time: Long, event: ScheduledEvent) {
        lastSequence++
        queue.add(Entry(time, lastSequence, event))
    }

    fun pollUntil(time: Long): Entry? {
        val next = queue.peek() ?: return null
        return if (next.time <= time) queue.poll() else null
    }

    class Entry(val time: Long, private val sequence: Long, val event: ScheduledEvent) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int =
            if (time != other.time) time.compareTo(other.time) else sequence.compareTo(other.sequence)
    }
}
