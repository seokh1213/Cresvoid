sealed interface Limiter {
    fun request(key: String, time: Long): Verdict
}
