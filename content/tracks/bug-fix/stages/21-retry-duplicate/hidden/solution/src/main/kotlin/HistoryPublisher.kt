enum class DeliveryStatus { CONFIRMED, UNKNOWN, REJECTED }

data class DeliveryResult(val status: DeliveryStatus, val attempts: Int) {
    fun describe(): String = "delivery=${status.name} attempts=$attempts"
}

class HistoryKeys {
    private var sequence = 0L

    fun next(): String {
        sequence++
        return "order-history-$sequence"
    }
}

class HistoryPublisher(
    private val store: HistoryStore,
    private val keys: HistoryKeys,
) {
    fun publish(change: StatusChange): DeliveryResult {
        val request = HistoryRequest(keys.next(), change)
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                store.append(request)
                return DeliveryResult(DeliveryStatus.CONFIRMED, attempt)
            } catch (_: HistoryTimeout) {
                if (attempt == MAX_ATTEMPTS) {
                    return DeliveryResult(DeliveryStatus.UNKNOWN, attempt)
                }
            } catch (_: HistoryRejected) {
                return DeliveryResult(DeliveryStatus.REJECTED, attempt)
            }
        }
        error("History delivery ended without a result")
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
