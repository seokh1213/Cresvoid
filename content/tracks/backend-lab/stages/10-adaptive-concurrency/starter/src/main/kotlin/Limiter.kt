class Limiter {
    fun tryAcquire(): Boolean = true

    fun onComplete(elapsedNanos: Long) {
        require(elapsedNanos >= 0)
    }
}
