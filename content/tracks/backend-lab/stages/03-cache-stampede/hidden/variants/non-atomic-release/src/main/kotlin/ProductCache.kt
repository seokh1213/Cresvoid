import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class ProductCache(private val redis: RedisClient, private val origin: Origin) {
    fun get(id: Int): String {
        val key = "product:$id"
        val lock = "lock:product:$id"
        while (true) {
            redis.get(key)?.let { return it }
            val token = UUID.randomUUID().toString()
            if (redis.set(lock, token, expireMillis = 1_800, onlyIfAbsent = true)) {
                try {
                    redis.get(key)?.let { return it }
                    val product = origin.fetch(id)
                    redis.set(key, product, expireMillis = 5_000)
                    return product
                } finally {
                    if (redis.get(lock) == token) redis.delete(lock)
                }
            }
            Thread.sleep(ThreadLocalRandom.current().nextLong(25, 51))
        }
    }
}
