class ClientThrottle(windowLength: Long, private val kMillis: Long, seed: Long) {
    private val history = ClientHistory(windowLength)
    private val random = ClientRandom(seed)
    private var retryAt = 0L

    fun tryRequest(time: Long, response: ServerResponse): ClientObservation {
        history.expire(time)
        val probability = history.probability(kMillis)
        val decision = decide(time, response, probability)
        if (decision is ClientDecision.Sent) updateRetryAt(time, decision.response)
        val accepted = decision is ClientDecision.Sent && decision.response == ServerResponse.Accepted
        history.record(time, accepted)
        return ClientObservation(decision, history.counts(), probability, retryAt)
    }

    fun stats(time: Long): ClientObservation {
        history.expire(time)
        return ClientObservation(ClientDecision.Stats, history.counts(), history.probability(kMillis), retryAt)
    }

    private fun decide(time: Long, response: ServerResponse, probability: DropProbability): ClientDecision {
        if (time < retryAt) return ClientDecision.Wait
        val draw = random.draw()
        return if (probability.rejects(draw)) ClientDecision.Drop(draw) else ClientDecision.Sent(response, draw)
    }

    private fun updateRetryAt(time: Long, response: ServerResponse) {
        val delay = when (response) {
            ServerResponse.Accepted -> null
            is ServerResponse.TooManyRequests -> response.retryAfterSeconds
            is ServerResponse.Unavailable -> response.retryAfterSeconds
        }
        if (delay != null) retryAt = maxOf(retryAt, time + 1000 * delay)
    }
}
