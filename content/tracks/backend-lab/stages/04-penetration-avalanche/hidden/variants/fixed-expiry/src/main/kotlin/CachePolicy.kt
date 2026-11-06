import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CachePolicy(private val redis: RedisClient, private val origin: Origin) {
    private val keyLocks = Array(1024) { ReentrantLock() }
    private val permits = Semaphore(12, true)
    private val pacing = ReentrantLock(true)
    private var nextStart = 0L

    fun ttlMillis(id: Int): Long = 10_000L

    fun get(id: Int): String? = keyLocks[id % keyLocks.size].withLock {
        val key = "product:$id"
        val cached = redis.get(key)
        if (cached != null) return@withLock cached.takeUnless { it == MISSING }
        val value = readOrigin(id)
        redis.set(key, value ?: MISSING, if (value == null) 2_000L else ttlMillis(id))
        value
    }

    private fun readOrigin(id: Int): String? {
        permits.acquire()
        try {
            pacing.withLock {
                val waitNanos = nextStart - System.nanoTime()
                if (waitNanos > 0) java.util.concurrent.TimeUnit.NANOSECONDS.sleep(waitNanos)
                nextStart = System.nanoTime() + 12_000_000L
            }
            return origin.fetch(id)
        } finally {
            permits.release()
        }
    }

    companion object {
        private const val MISSING = "!missing"
    }
}
