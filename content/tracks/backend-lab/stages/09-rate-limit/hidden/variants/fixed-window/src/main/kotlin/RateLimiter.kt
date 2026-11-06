data class Decision(val allowed: Boolean, val atMicros: Long)

class RateLimiter(private val redis: RedisClient, private val prefix: String) {
    fun allow(user: String): Decision {
        val reply = redis.call("EVAL", ADMIT, "1", "$prefix:limit:$user") as List<*>
        return Decision(reply[0] == 1L, reply[1] as Long)
    }

    private companion object {
        val ADMIT = """
            local clock = redis.call('TIME')
            local now = tonumber(clock[1]) * 1000000 + tonumber(clock[2])
            local second = tonumber(clock[1])
            local previous = tonumber(redis.call('HGET', KEYS[1], 'second') or '-1')
            if previous ~= second then
                redis.call('HSET', KEYS[1], 'second', second, 'count', 0)
            end
            local count = redis.call('HINCRBY', KEYS[1], 'count', 1)
            redis.call('PEXPIRE', KEYS[1], 2000)
            return {count <= 30 and 1 or 0, now}
        """.trimIndent()
    }
}
