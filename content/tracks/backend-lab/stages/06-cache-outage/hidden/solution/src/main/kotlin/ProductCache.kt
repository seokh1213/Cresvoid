import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

class ProductCache(originUrl: String, redisPort: Int) : Closeable {
    private data class Entry(val body: String, val loadedAt: Long)
    private val redis = RedisClient("127.0.0.1", redisPort, timeoutMillis = 120)
    private val origin = OriginClient(originUrl)
    private val local = ConcurrentHashMap<Int, Entry>()
    private val locks = ConcurrentHashMap<Int, ReentrantLock>()
    private val nextOrigin = AtomicLong()
    private val nextProbe = AtomicLong()
    @Volatile private var blockedUntil = 0L
    private val loadedAt = Regex("\"loadedAtMillis\":(\\d+)")

    fun get(id: Int): String? {
        val previous = local[id]
        if (previous != null && age(previous) < 1_500) return previous.body
        val lock = locks.computeIfAbsent(id) { ReentrantLock() }
        if (!lock.tryLock()) return previous?.takeIf { age(it) < 6_000 }?.body
        try {
            val current = local[id]
            if (current != null && age(current) < 1_500) return current.body
            val cacheAvailable = mayCallRedis()
            if (cacheAvailable) {
                val cached = redisCall { redis.get("product:$id") }?.let(::entry)
                if (cached != null && age(cached) < 1_500) {
                    local[id] = cached
                    return cached.body
                }
            }
            if (!admitOrigin()) return current?.takeIf { age(it) < 6_000 }?.body
            val refreshed = origin.get(id)?.let(::entry)
                ?: return current?.takeIf { age(it) < 6_000 }?.body
            local[id] = refreshed
            if (cacheAvailable && System.nanoTime() >= blockedUntil) {
                redisCall { redis.set("product:$id", refreshed.body, expireMillis = 5_000) }
            }
            return refreshed.body
        } finally {
            lock.unlock()
        }
    }

    private fun entry(body: String): Entry? = loadedAt.find(body)?.groupValues?.get(1)?.toLongOrNull()
        ?.let { Entry(body, it) }

    private fun age(entry: Entry): Long = System.currentTimeMillis() - entry.loadedAt

    private fun admitOrigin(): Boolean {
        while (true) {
            val now = System.nanoTime()
            val next = nextOrigin.get()
            if (now < next) return false
            if (nextOrigin.compareAndSet(next, now + 12_500_000)) return true
        }
    }

    private fun mayCallRedis(): Boolean {
        val now = System.nanoTime()
        if (now < blockedUntil) return false
        if (blockedUntil == 0L) return true
        val previous = nextProbe.get()
        return now >= previous && nextProbe.compareAndSet(previous, now + 500_000_000)
    }

    private fun <T> redisCall(command: () -> T): T? = try {
        command().also { blockedUntil = 0L }
    } catch (_: IOException) {
        blockedUntil = System.nanoTime() + 500_000_000
        null
    }

    override fun close() = redis.close()
}
