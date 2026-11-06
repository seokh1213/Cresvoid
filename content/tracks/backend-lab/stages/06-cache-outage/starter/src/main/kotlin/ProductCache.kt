import java.io.Closeable
import java.io.IOException

class ProductCache(originUrl: String, redisPort: Int) : Closeable {
    private val redis = RedisClient("127.0.0.1", redisPort)
    private val origin = OriginClient(originUrl)

    fun get(id: Int): String? {
        val cached = try { redis.get("product:$id") } catch (_: IOException) { null }
        if (cached != null) return cached
        val product = origin.get(id) ?: return null
        try { redis.set("product:$id", product, expireMillis = 5_000) } catch (_: IOException) { }
        return product
    }

    override fun close() = redis.close()
}
