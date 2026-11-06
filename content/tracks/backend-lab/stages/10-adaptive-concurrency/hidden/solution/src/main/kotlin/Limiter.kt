import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.max

class Limiter {
    private var inFlight = 0
    private var limit = 2
    private var windowBegan = System.nanoTime()
    private var limitChangedAt = windowBegan
    private val samples = mutableListOf<Long>()
    private val baselineWindows = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        if (inFlight >= limit) return false
        inFlight++
        return true
    }

    @Synchronized
    fun onComplete(elapsedNanos: Long) {
        check(inFlight > 0)
        inFlight--
        val now = System.nanoTime()
        // Completions admitted before a reduction must not trigger another reduction.
        if (now - elapsedNanos < limitChangedAt) return
        samples += elapsedNanos
        val baselineEstimate = baselineWindows.minOrNull() ?: samples.min()
        val observationNanos = max(300_000_000L, baselineEstimate * 4)
        if (now - windowBegan < observationNanos || samples.size < 2) return
        baselineWindows.addLast(samples.min())
        // Expire old minima so a permanent change in service time can become the new baseline.
        if (baselineWindows.size > 50) baselineWindows.removeFirst()
        val baseline = baselineWindows.min()
        val ordered = samples.sorted()
        val latency = ordered[(ceil(ordered.size * 0.9).toInt() - 1).coerceAtLeast(0)]
        val nextLimit = when {
            latency > baseline * 2.0 -> max(1, (limit * 0.65).toInt())
            latency > baseline * 1.3 -> max(1, limit - 1)
            latency < baseline * 1.15 && inFlight >= limit - 2 -> (limit + 1).coerceAtMost(128)
            else -> limit
        }
        if (nextLimit < limit) limitChangedAt = now
        limit = nextLimit
        samples.clear()
        windowBegan = now
    }
}
