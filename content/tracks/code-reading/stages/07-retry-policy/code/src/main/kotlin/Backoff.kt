import kotlin.random.Random

class Backoff(private val policy: RetryPolicy, seed: Int) {
    private val random = Random(seed)

    fun nextDelay(failedAttempt: Int): Long {
        require(failedAttempt >= 1)
        // Keep the Int multiplier positive even when restoring a long retry history.
        val exponent = minOf(failedAttempt - 1, 30)
        val multiplier = 1 shl exponent
        val expandedMs = (policy.baseMs * multiplier).toLong()
        val cappedMs = minOf(expandedMs, policy.ceilingMs)
        return when (policy.jitter) {
            Jitter.NONE -> cappedMs
            Jitter.FULL -> random.nextLong(cappedMs)
            Jitter.EQUAL -> {
                val floorMs = cappedMs / 2
                floorMs + random.nextLong(cappedMs - floorMs)
            }
        }
    }
}
