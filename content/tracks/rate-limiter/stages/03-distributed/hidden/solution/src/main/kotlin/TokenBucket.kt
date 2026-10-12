const val MILLI_TOKENS_PER_TOKEN = 1000L

class TokenBucket(capacity: Long, private val refillPerSecond: Long) : RequestLimiter {
    private val capacityMilliTokens = capacity * MILLI_TOKENS_PER_TOKEN
    private val buckets = HashMap<String, Bucket>()

    override fun request(key: String, time: Long): TokenVerdict {
        val milliTokens = buckets[key]?.milliTokensAt(time) ?: capacityMilliTokens
        if (milliTokens < MILLI_TOKENS_PER_TOKEN) {
            buckets[key] = Bucket(time, milliTokens)
            return TokenVerdict.Deny(milliTokens, retryAt = time + millisUntilOneToken(milliTokens))
        }
        val remaining = milliTokens - MILLI_TOKENS_PER_TOKEN
        buckets[key] = Bucket(time, remaining)
        return TokenVerdict.Allow(remaining)
    }

    // 초당 r 개를 채우면 1밀리초에 정확히 r 밀리토큰이 차므로 정수로 계산해도 오차가 없다
    private fun Bucket.milliTokensAt(time: Long): Long =
        minOf(capacityMilliTokens, milliTokens + refillPerSecond * (time - lastRequestTime))

    private fun millisUntilOneToken(milliTokens: Long): Long =
        Math.ceilDiv(MILLI_TOKENS_PER_TOKEN - milliTokens, refillPerSecond)

    private class Bucket(val lastRequestTime: Long, val milliTokens: Long)
}
