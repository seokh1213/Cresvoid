class Limiter {
    private var inFlight = 0

    @Synchronized
    fun tryAcquire(): Boolean {
        if (inFlight >= 12) return false
        inFlight++
        return true
    }

    @Synchronized
    fun onComplete(elapsedNanos: Long) {
        require(elapsedNanos >= 0)
        inFlight--
    }
}
