import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

class EventLog(private val clock: () -> Long) {
    private val events = mutableListOf<Event>()

    fun record(text: String) {
        events += Event(clock(), text)
    }

    fun lines(): List<String> =
        events.sortedWith(compareBy({ it.time }, { it.text })).map { "${it.time} ${it.text}" }

    private class Event(val time: Long, val text: String)
}

suspend fun EventLog.awaitResponse(call: String, latency: Long) {
    try {
        delay(latency)
    } catch (cancellation: CancellationException) {
        record("cancel $call")
        throw cancellation
    }
}
