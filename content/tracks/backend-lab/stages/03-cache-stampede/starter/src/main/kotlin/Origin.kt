class Origin(private val redis: RedisClient, private val delayMillis: Long) {
    fun fetch(id: Int): String {
        redis.increment("origin:calls:$id")
        while (redis.get("origin:gate:$id") == "closed") Thread.sleep(5)
        Thread.sleep(delayMillis)
        return """{"id":$id,"name":"product-$id","price":${id * 100}}"""
    }
}
