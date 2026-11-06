import java.util.concurrent.ConcurrentHashMap

class IdempotencyStore(private val gateway: PaymentGateway) {
    private class Entry(val request: PaymentRequest) {
        var response: PaymentResponse? = null
    }
    private val entries = ConcurrentHashMap<String, Entry>()

    fun execute(key: String, request: PaymentRequest): PaymentResponse {
        val entry = entries.computeIfAbsent(key) { Entry(request) }
        if (entry.request != request) return keyConflict()
        return synchronized(entry) {
            entry.response ?: gateway.charge(key, request).also { response ->
                if (response.status == 201) entry.response = response
            }
        }
    }
}
