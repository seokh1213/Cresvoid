import kotlinx.coroutines.withTimeout

data class CallPolicy(val attemptTimeout: Long, val maxAttempts: Int)

enum class CallResult { COMPLETED, REJECTED, UNAVAILABLE }

class DownstreamClient(private val downstream: FakeDownstream) {
    suspend fun call(orderId: String, service: String, policy: CallPolicy): CallResult {
        repeat(policy.maxAttempts) {
            val result = attempt(orderId, service, policy.attemptTimeout)
            if (result != CallResult.UNAVAILABLE) return result
        }
        return CallResult.UNAVAILABLE
    }

    private suspend fun attempt(orderId: String, service: String, attemptTimeout: Long): CallResult =
        try {
            withTimeout(attemptTimeout) { downstream.send(orderId, service) }
            CallResult.COMPLETED
        } catch (rejected: ServiceRejected) {
            CallResult.REJECTED
        } catch (failure: Exception) {
            CallResult.UNAVAILABLE
        }
}
