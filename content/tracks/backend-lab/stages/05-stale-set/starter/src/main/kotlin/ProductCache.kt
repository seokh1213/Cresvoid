class ProductCache(private val source: Source, private val redis: RedisClient) {
    fun get(id: Int): Product {
        val cached = redis.get("product:$id")
        if (cached != null) return Product.decode(cached)
        val snapshot = source.read(id)
        redis.set("product:$id", snapshot.encode(), expireMillis = 30_000)
        return snapshot
    }

    fun update(id: Int, price: Long): Product {
        val product = source.update(id, price)
        redis.delete("product:$id")
        return product
    }
}
