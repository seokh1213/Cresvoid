import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class IdempotencyStore(private val gateway: PaymentGateway) {
    private class Entry(val request: PaymentRequest) {
        val response = CompletableFuture<PaymentResponse>()
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    fun execute(key: String, request: PaymentRequest): PaymentResponse {
        val candidate = Entry(request)
        val existing = entries.putIfAbsent(key, candidate)
        if (existing != null) {
            if (existing.request != request) {
                if (!existing.response.isDone) return PaymentResponse(409, """{"error":"processing"}""")
                return keyConflict()
            }
            return existing.response.join()
        }
        try {
            val response = gateway.charge(key, request)
            candidate.response.complete(response)
            return response
        } catch (error: Exception) {
            // Release waiters without allowing another uncertain charge.
            candidate.response.completeExceptionally(error)
            throw error
        }
    }
}
