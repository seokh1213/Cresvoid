data class RetryPolicy(
    val name: String,
    val baseMs: Double,
    val ceilingMs: Long,
    val maxAttempts: Int,
    val jitter: Jitter
) {
    init {
        require(baseMs.isFinite() && baseMs >= 1) { "invalid-base" }
        require(ceilingMs > 0) { "invalid-ceiling" }
        require(maxAttempts in 1..100) { "invalid-attempt-limit" }
    }
}

enum class Jitter {
    NONE,
    FULL,
    EQUAL
}

data class RetryCheckpoint(val completedAttempts: Int, val remainingMs: Long)

class RetrySession(
    private val paymentId: String,
    private val policy: RetryPolicy,
    seed: Int,
    checkpoint: RetryCheckpoint
) {
    private val backoff = Backoff(policy, seed)
    private var attempts = checkpoint.completedAttempts
    private var remainingMs = checkpoint.remainingMs
    private var closed = false

    init {
        require(attempts in 0 until policy.maxAttempts) { "invalid-checkpoint" }
        require(remainingMs >= 0) { "invalid-budget" }
    }

    fun call(outcome: GatewayOutcome): String {
        if (closed) return report("IGNORED", "CLOSED")
        attempts++
        val failure = callGateway(outcome)
        if (failure == null) {
            closed = true
            return report("SUCCESS", "OK")
        }
        if (!failure.retryable) {
            closed = true
            return report("STOP", failure.reason)
        }
        if (attempts >= policy.maxAttempts) {
            closed = true
            return report("STOP", "LIMIT:${failure.reason}")
        }
        val waitMs = backoff.nextDelay(attempts)
        if (waitMs > remainingMs) {
            closed = true
            return report("STOP", "BUDGET:${failure.reason}")
        }
        remainingMs -= waitMs
        return report("RETRY", failure.reason, waitMs)
    }

    private fun report(result: String, reason: String, waitMs: Long = 0): String =
        "$paymentId attempt=$attempts result=$result reason=$reason " +
            "wait=$waitMs remaining=$remainingMs"
}
