import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class IdempotencyStore(private val gateway: PaymentGateway) {
    private val entries = ConcurrentHashMap<String, CompletableFuture<PaymentResponse>>()

    fun execute(key: String, request: PaymentRequest): PaymentResponse {
        val candidate = CompletableFuture<PaymentResponse>()
        val existing = entries.putIfAbsent(key, candidate)
        if (existing != null) return existing.join()
        try {
            val response = gateway.charge(key, request)
            candidate.complete(response)
            return response
        } catch (error: Exception) {
            candidate.completeExceptionally(error)
            throw error
        }
    }
}
