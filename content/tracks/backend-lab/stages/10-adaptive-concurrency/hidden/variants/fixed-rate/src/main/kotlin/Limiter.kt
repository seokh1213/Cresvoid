class Limiter {
    private var nextPermit = System.nanoTime()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = System.nanoTime()
        if (now < nextPermit) return false
        nextPermit = now + 25_000_000
        return true
    }

    fun onComplete(elapsedNanos: Long) {
        require(elapsedNanos >= 0)
    }
}
