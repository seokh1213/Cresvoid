import kotlinx.coroutines.asContextElement
import java.util.Collections
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

enum class SpanOutcome { OK, ERROR, CANCELLED }

data class Span(
    val traceId: String?,
    val name: String,
    val startMillis: Long,
    val endMillis: Long,
    val outcome: SpanOutcome,
)

class Tracer(private val clock: () -> Long) {
    private val currentTraceId = ThreadLocal<String?>()
    private val finished: MutableList<Span> = Collections.synchronizedList(mutableListOf())

    fun context(traceId: String): CoroutineContext = currentTraceId.asContextElement(traceId)

    suspend fun <T> span(name: String, block: suspend () -> T): T {
        val traceId = currentTraceId.get()
        val startMillis = clock()
        var outcome = SpanOutcome.ERROR
        try {
            return block().also { outcome = SpanOutcome.OK }
        } catch (cancellation: CancellationException) {
            outcome = SpanOutcome.CANCELLED
            throw cancellation
        } finally {
            finished += Span(traceId, name, startMillis, clock(), outcome)
        }
    }

    fun spansOf(traceId: String): List<Span> =
        synchronized(finished) { finished.filter { it.traceId == traceId } }
}
