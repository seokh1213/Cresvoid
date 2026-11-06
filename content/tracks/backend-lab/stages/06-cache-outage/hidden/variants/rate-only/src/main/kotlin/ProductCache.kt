import java.io.Closeable
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class ProductCache(originUrl: String, redisPort: Int) : Closeable {
    private val redis = RedisClient("127.0.0.1", redisPort, timeoutMillis = 120)
    private val origin = OriginClient(originUrl)
    private val nextOrigin = AtomicLong()
    @Volatile private var blockedUntil = 0L

    fun get(id: Int): String? {
        if (System.nanoTime() >= blockedUntil) {
            val cached = cacheRead(id)
            if (cached != null) return cached
        }
        if (!admit()) return null
        val product = origin.get(id) ?: return null
        if (System.nanoTime() >= blockedUntil) {
            try { redis.set("product:$id", product, expireMillis = 5_000) } catch (_: IOException) {
                blockedUntil = System.nanoTime() + 500_000_000
            }
        }
        return product
    }

    private fun cacheRead(id: Int): String? = try {
        redis.get("product:$id")
    } catch (_: IOException) {
        blockedUntil = System.nanoTime() + 500_000_000
        null
    }

    private fun admit(): Boolean {
        while (true) {
            val now = System.nanoTime()
            val previous = nextOrigin.get()
            if (now < previous) return false
            if (nextOrigin.compareAndSet(previous, now + 12_500_000)) return true
        }
    }

    override fun close() = redis.close()
}
