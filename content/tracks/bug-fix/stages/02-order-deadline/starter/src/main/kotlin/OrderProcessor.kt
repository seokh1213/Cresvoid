import kotlinx.coroutines.withTimeoutOrNull

data class ConfirmationStep(val service: String, val required: Boolean, val policy: CallPolicy)

val CONFIRMATION_STEPS = listOf(
    ConfirmationStep("stock", required = true, policy = CallPolicy(attemptTimeout = 200, maxAttempts = 2)),
    ConfirmationStep("payment", required = true, policy = CallPolicy(attemptTimeout = 300, maxAttempts = 3)),
    ConfirmationStep("points", required = false, policy = CallPolicy(attemptTimeout = 100, maxAttempts = 2)),
    ConfirmationStep("notification", required = false, policy = CallPolicy(attemptTimeout = 100, maxAttempts = 2)),
)

sealed interface OrderOutcome {
    data class Confirmed(val skippedServices: List<String>) : OrderOutcome

    data class Failed(val service: String, val reason: CallResult) : OrderOutcome

    data object TimedOut : OrderOutcome
}

data class OrderResult(val orderId: String, val receivedAt: Long, val elapsed: Long, val outcome: OrderOutcome)

class OrderProcessor(private val client: DownstreamClient, private val clock: () -> Long) {
    suspend fun process(orderId: String, timeLimit: Long): OrderResult {
        val receivedAt = clock()
        val outcome = withTimeoutOrNull(timeLimit) { runSteps(orderId) } ?: OrderOutcome.TimedOut
        return OrderResult(orderId, receivedAt, elapsed = clock() - receivedAt, outcome)
    }

    private suspend fun runSteps(orderId: String): OrderOutcome {
        val skippedServices = mutableListOf<String>()
        for (step in CONFIRMATION_STEPS) {
            val result = client.call(orderId, step.service, step.policy)
            if (result == CallResult.COMPLETED) continue
            if (step.required) return OrderOutcome.Failed(step.service, result)
            skippedServices += step.service
        }
        return OrderOutcome.Confirmed(skippedServices)
    }
}
