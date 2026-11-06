class ProductCache(private val source: Source, private val redis: RedisClient) {
    private val publish = """
        local floor = tonumber(redis.call('GET', KEYS[2]) or '0')
        local current = redis.call('GET', KEYS[1])
        local version = tonumber(ARGV[2])
        if version < floor then return 0 end
        if current and tonumber(string.match(current, '(%d+)$')) > version then return 0 end
        redis.call('SET', KEYS[1], ARGV[1], 'PX', 30000)
        return 1
    """.trimIndent()
    private val invalidate = """
        local floor = tonumber(redis.call('GET', KEYS[2]) or '0')
        local version = tonumber(ARGV[1])
        if version > floor then redis.call('SET', KEYS[2], ARGV[1]) end
        local current = redis.call('GET', KEYS[1])
        if current and tonumber(string.match(current, '(%d+)$')) < math.max(floor, version) then
            redis.call('DEL', KEYS[1])
        end
        return 1
    """.trimIndent()

    fun get(id: Int): Product {
        while (true) {
            val cached = redis.get("product:$id")
            if (cached != null) return Product.decode(cached)
            val snapshot = source.read(id)
            val accepted = redis.call(
                "EVAL", publish, "2", "product:$id", "floor:$id", snapshot.encode(), snapshot.version.toString(),
            )
            if (accepted == 1L) return snapshot
        }
    }

    fun update(id: Int, price: Long): Product {
        val product = source.update(id, price)
        // Deleting a value must not erase the version that rejects an older in-flight reader.
        redis.call("EVAL", invalidate, "2", "product:$id", "floor:$id", product.version.toString())
        return product
    }
}
