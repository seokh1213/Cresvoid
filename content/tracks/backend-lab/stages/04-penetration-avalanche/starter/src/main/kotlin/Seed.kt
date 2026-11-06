class Seed(private val redis: RedisClient, private val policy: CachePolicy) {
    fun fill(): String {
        val epoch = System.currentTimeMillis()
        val ttls = (1..10_000).map(policy::ttlMillis)
        for (chunk in (1..10_000).chunked(250)) {
            val arguments = chunk.flatMap { id ->
                listOf("product:$id", productBody(id), (epoch + ttls[id - 1]).toString())
            }
            redis.call("EVAL", SET_BATCH, "0", *arguments.toTypedArray())
        }
        val span = ttls.max() - ttls.min()
        val bins = ttls.map { (it - 5_000L) / 1_000L }.toSet().size
        val invalid = ttls.count { it !in 5_000L..15_000L }
        return "{\"span\":$span,\"bins\":$bins,\"invalid\":$invalid}"
    }

    companion object {
        private const val SET_BATCH = "for i=1,#ARGV,3 do " +
            "redis.call('SET',ARGV[i],ARGV[i+1],'PXAT',ARGV[i+2]) end return 1"
    }
}
