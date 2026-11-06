import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CachePolicy(private val redis: RedisClient, private val origin: Origin) {
    private val keyLocks = Array(1024) { ReentrantLock() }
    private val permits = Semaphore(12, true)

    fun ttlMillis(id: Int): Long = 5_000L + (id.toLong() * 7919L % 10_001L)

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
            return origin.fetch(id)
        } finally {
            permits.release()
        }
    }

    companion object {
        private const val MISSING = "!missing"
    }
}
