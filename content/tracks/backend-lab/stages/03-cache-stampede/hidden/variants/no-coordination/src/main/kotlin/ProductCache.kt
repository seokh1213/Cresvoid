class ProductCache(private val redis: RedisClient, private val origin: Origin) {
    fun get(id: Int): String {
        val key = "product:$id"
        redis.get(key)?.let { return it }
        val product = origin.fetch(id)
        redis.set(key, product, expireMillis = 5_000)
        return product
    }
}
