sealed interface Limiter

sealed interface RequestLimiter : Limiter {
    fun request(key: String, time: Long): Verdict
}
