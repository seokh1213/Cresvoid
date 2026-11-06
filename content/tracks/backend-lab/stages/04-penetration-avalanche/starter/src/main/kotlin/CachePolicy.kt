import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CachePolicy(private val redis: RedisClient, private val origin: Origin) {
    private val keyLocks = Array(1024) { ReentrantLock() }

    fun ttlMillis(id: Int): Long = 5_000L

    fun get(id: Int): String? = keyLocks[id % keyLocks.size].withLock {
        val cached = redis.get("product:$id")
        if (cached != null) return@withLock cached
        val value = origin.fetch(id)
        if (value != null) redis.set("product:$id", value, ttlMillis(id))
        value
    }
}
