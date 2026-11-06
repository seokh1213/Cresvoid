import java.util.UUID

data class Decision(val allowed: Boolean, val atMicros: Long)

class RateLimiter(private val redis: RedisClient, private val prefix: String) {
    fun allow(user: String): Decision {
        val time = redis.call("TIME") as List<*>
        val now = time[0].toString().toLong() * 1_000_000 + time[1].toString().toLong()
        val key = "$prefix:limit:$user"
        redis.call("ZREMRANGEBYSCORE", key, "-inf", (now - 1_000_000).toString())
        val count = redis.call("ZCARD", key) as Long
        Thread.sleep(20)
        val allowed = count < 30
        if (allowed) redis.call("ZADD", key, now.toString(), UUID.randomUUID().toString())
        redis.expireMillis(key, 2_000)
        return Decision(allowed, now)
    }
}
