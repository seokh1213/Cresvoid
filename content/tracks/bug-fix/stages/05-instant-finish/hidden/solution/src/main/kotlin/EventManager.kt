import kotlinx.coroutines.CoroutineScope

interface EventTime {
    fun now(): Long
    fun advanceBy(milliseconds: Long)
    suspend fun pause(milliseconds: Long)
}

class EventManager(
    private val scope: CoroutineScope,
    private val time: EventTime,
) {
    private val events = mutableMapOf<String, CatalogEvent>()

    fun start(id: String, duration: Long, interval: Long): String {
        if (id in events) return "ERROR EXISTS $id"

        val plan = EventPlan(id, time.now(), duration, interval)
        val event = CatalogEvent(plan, time) { finishedId ->
            events.remove(finishedId)
        }
        events[id] = event
        event.start(scope)
        return "STARTED $id"
    }

    fun status(id: String): String {
        val event = events[id] ?: return "ERROR NOT_FOUND $id"
        return event.snapshot().render()
    }

    fun list(): String {
        val ids = events.keys.sorted()
        return if (ids.isEmpty()) "EVENTS -" else "EVENTS ${ids.joinToString(" ")}"
    }

    suspend fun stop(id: String): String {
        val event = events[id] ?: return "ERROR NOT_FOUND $id"
        event.stop()
        return "STOPPED $id"
    }

    suspend fun close() {
        for (event in events.values.toList()) {
            event.stop()
        }
    }
}
