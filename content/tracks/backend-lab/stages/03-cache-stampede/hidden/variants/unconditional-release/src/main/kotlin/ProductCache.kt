import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class ProductCache(private val redis: RedisClient, private val origin: Origin) {
    private val release = """
        if redis.call('GET', KEYS[1]) == ARGV[1] then
            return redis.call('DEL', KEYS[1])
        end
        return 0
    """.trimIndent()

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
                    redis.delete(lock)
                }
            }
            Thread.sleep(ThreadLocalRandom.current().nextLong(25, 51))
        }
    }
}
