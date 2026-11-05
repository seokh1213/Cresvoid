class ClientHistory(private val windowLength: Long) {
    private val attempts = ArrayDeque<Attempt>()
    private var accepts = 0L

    fun expire(time: Long) {
        val cutoff = time - windowLength
        while (attempts.isNotEmpty() && attempts.first().time <= cutoff) {
            if (attempts.removeFirst().accepted) accepts--
        }
    }

    fun record(time: Long, accepted: Boolean) {
        attempts.addLast(Attempt(time, accepted))
        if (accepted) accepts++
    }

    fun counts(): ClientCounts = ClientCounts(attempts.size.toLong(), accepts)

    fun probability(kMillis: Long): DropProbability {
        val requests = attempts.size.toLong()
        return DropProbability(maxOf(0L, 1000 * requests - kMillis * accepts), 1000 * (requests + 1))
    }

    private class Attempt(val time: Long, val accepted: Boolean)
}

data class ClientCounts(val requests: Long, val accepts: Long)
