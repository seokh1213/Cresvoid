import java.util.concurrent.ConcurrentHashMap

data class Decision(val allowed: Boolean, val atMicros: Long)

class RateLimiter(private val redis: RedisClient, private val prefix: String) {
    private val windows = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun allow(user: String): Decision {
        val window = windows.computeIfAbsent(user) { ArrayDeque() }
        return synchronized(window) {
            val time = redis.call("TIME") as List<*>
            val now = time[0].toString().toLong() * 1_000_000 + time[1].toString().toLong()
            while (window.isNotEmpty() && window.first() <= now - 1_000_000) window.removeFirst()
            val allowed = window.size < 30
            if (allowed) window.addLast(now)
            Decision(allowed, now)
        }
    }
}
