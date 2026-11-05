import java.util.ArrayDeque

enum class CallOutcome { OK, BEFORE, AFTER, DENY }

data class StatusChange(
    val orderId: String,
    val previous: OrderStatus,
    val current: OrderStatus,
) {
    fun describe(): String = "${previous.name}>${current.name}"
}

data class HistoryRequest(val idempotencyKey: String, val change: StatusChange)

class HistoryTimeout : RuntimeException()
class HistoryRejected : RuntimeException()

interface HistoryStore {
    fun append(request: HistoryRequest)
}

class ScriptedHistoryStore : HistoryStore {
    private val outcomes = ArrayDeque<CallOutcome>()
    private val savedKeys = mutableSetOf<String>()
    private val records = mutableListOf<StatusChange>()
    private var calls = 0

    fun schedule(additions: List<CallOutcome>): String {
        outcomes.addAll(additions)
        return "PLANNED ${outcomes.size}"
    }

    override fun append(request: HistoryRequest) {
        calls++
        val outcome = outcomes.pollFirst() ?: CallOutcome.OK
        when (outcome) {
            CallOutcome.BEFORE -> throw HistoryTimeout()
            CallOutcome.DENY -> throw HistoryRejected()
            CallOutcome.OK, CallOutcome.AFTER -> save(request)
        }
        if (outcome == CallOutcome.AFTER) throw HistoryTimeout()
    }

    private fun save(request: HistoryRequest) {
        if (savedKeys.add(request.idempotencyKey)) {
            records.add(request.change)
        }
    }

    fun history(orderId: String): String {
        val transitions = records.filter { it.orderId == orderId }
            .joinToString("|") { it.describe() }
        return "HISTORY $orderId ${transitions.ifEmpty { "-" }}"
    }

    fun metrics(): String =
        "STORE calls=$calls records=${records.size} queued=${outcomes.size}"
}
