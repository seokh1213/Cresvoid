import java.util.UUID

data class Decision(val allowed: Boolean, val atMicros: Long)

class RateLimiter(private val redis: RedisClient, private val prefix: String) {
    fun allow(user: String): Decision {
        val reply = redis.call("EVAL", ADMIT, "1", "$prefix:limit:$user", UUID.randomUUID().toString()) as List<*>
        return Decision(reply[0] == 1L, reply[1] as Long)
    }

    private companion object {
        val ADMIT = """
            local clock = redis.call('TIME')
            local now = tonumber(clock[1]) * 1000000 + tonumber(clock[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - 1000000)
            local allowed = 0
            if redis.call('ZCARD', KEYS[1]) < 30 then
                redis.call('ZADD', KEYS[1], now, ARGV[1])
                allowed = 1
            end
            redis.call('PEXPIRE', KEYS[1], 2000)
            return {allowed, now}
        """.trimIndent()
    }
}
