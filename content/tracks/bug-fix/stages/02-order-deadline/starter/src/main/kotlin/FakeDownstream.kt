import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

enum class RequestOutcome { OK, REJECTED, UNAVAILABLE, CANCELLED }

enum class ReplyStatus(val outcome: RequestOutcome) {
    OK(RequestOutcome.OK),
    REJECTED(RequestOutcome.REJECTED),
    UNAVAILABLE(RequestOutcome.UNAVAILABLE),
}

data class ScriptedReply(val status: ReplyStatus, val latency: Long)

data class RequestRecord(
    val orderId: String,
    val service: String,
    val attempt: Int,
    val sentAt: Long,
    val endedAt: Long,
    val outcome: RequestOutcome,
)

class ServiceRejected(service: String) : RuntimeException("$service rejected the request")

class ServiceUnavailable(service: String) : RuntimeException("$service is unavailable")

private val TYPICAL_LATENCY = mapOf("stock" to 30L, "payment" to 50L, "points" to 20L, "notification" to 10L)

private data class ScriptKey(val orderId: String, val service: String)

private class ReplyScript {
    val pendingReplies = ArrayDeque<ScriptedReply>()
    var requestsSent = 0
}

class FakeDownstream(private val clock: () -> Long) {
    val services: Set<String> = TYPICAL_LATENCY.keys

    private val scripts = mutableMapOf<ScriptKey, ReplyScript>()
    private val records = mutableListOf<RequestRecord>()

    fun script(orderId: String, service: String, reply: ScriptedReply) {
        scriptOf(orderId, service).pendingReplies.addLast(reply)
    }

    suspend fun send(orderId: String, service: String) {
        val script = scriptOf(orderId, service)
        script.requestsSent += 1
        val attempt = script.requestsSent
        val reply = script.pendingReplies.removeFirstOrNull()
            ?: ScriptedReply(ReplyStatus.OK, TYPICAL_LATENCY.getValue(service))
        val sentAt = clock()
        fun record(outcome: RequestOutcome) {
            records += RequestRecord(orderId, service, attempt, sentAt, endedAt = clock(), outcome = outcome)
        }
        try {
            delay(reply.latency)
        } catch (cancellation: CancellationException) {
            record(RequestOutcome.CANCELLED)
            throw cancellation
        }
        record(reply.status.outcome)
        when (reply.status) {
            ReplyStatus.OK -> Unit
            ReplyStatus.REJECTED -> throw ServiceRejected(service)
            ReplyStatus.UNAVAILABLE -> throw ServiceUnavailable(service)
        }
    }

    fun requestsOf(orderId: String): List<RequestRecord> = records.filter { it.orderId == orderId }

    private fun scriptOf(orderId: String, service: String): ReplyScript =
        scripts.getOrPut(ScriptKey(orderId, service)) { ReplyScript() }
}
