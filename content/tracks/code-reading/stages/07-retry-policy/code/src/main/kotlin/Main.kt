fun main() {
    val console = RetryConsole()
    System.`in`.bufferedReader().forEachLine { line ->
        if (line.isNotBlank()) {
            try {
                console.execute(line.trim().split(Regex("\\s+")))?.let(::println)
            } catch (failure: IllegalArgumentException) {
                println("ERROR ${failure.message ?: "invalid-command"}")
            }
        }
    }
}

private class RetryConsole {
    private val policies = mutableMapOf<String, RetryPolicy>()
    private val sessions = mutableMapOf<String, RetrySession>()

    fun execute(tokens: List<String>): String? = when (tokens.first()) {
        "POLICY" -> {
            registerPolicy(tokens)
            null
        }
        "OPEN" -> {
            openPayment(tokens)
            null
        }
        "CALL" -> executeCall(tokens)
        else -> throw IllegalArgumentException("unknown-command")
    }

    private fun registerPolicy(tokens: List<String>) {
        require(tokens.size == 6) { "policy-arguments" }
        val policy = RetryPolicy(
            name = tokens[1],
            baseMs = tokens[2].toDouble(),
            ceilingMs = tokens[3].toLong(),
            maxAttempts = tokens[4].toInt(),
            jitter = Jitter.valueOf(tokens[5])
        )
        policies[policy.name] = policy
    }

    private fun openPayment(tokens: List<String>) {
        require(tokens.size == 6) { "open-arguments" }
        val policy = requireNotNull(policies[tokens[2]]) { "unknown-policy" }
        val checkpoint = RetryCheckpoint(
            completedAttempts = tokens[4].toInt(),
            remainingMs = tokens[5].toLong()
        )
        val session = RetrySession(
            paymentId = tokens[1],
            policy = policy,
            seed = tokens[3].toInt(),
            checkpoint = checkpoint
        )
        sessions[tokens[1]] = session
    }

    private fun executeCall(tokens: List<String>): String {
        require(tokens.size == 3) { "call-arguments" }
        val session = requireNotNull(sessions[tokens[1]]) { "unknown-payment" }
        val outcome = parseGatewayOutcome(tokens[2])
        return session.call(outcome)
    }
}
