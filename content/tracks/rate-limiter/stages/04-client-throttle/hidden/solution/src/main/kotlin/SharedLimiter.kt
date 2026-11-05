sealed class SharedLimiter(private val limit: Long, private val windowLength: Long) : Limiter {
    fun windowOf(time: Long): Long = time / windowLength

    fun judge(count: Long): RequestResult = if (count <= limit) RequestResult.Allow(count) else RequestResult.Deny(count)
}

class ReadWriteLimiter(limit: Long, windowLength: Long) : SharedLimiter(limit, windowLength)

class AtomicLimiter(limit: Long, windowLength: Long) : SharedLimiter(limit, windowLength)

class LocalCopyLimiter(limit: Long, windowLength: Long, val syncPeriod: Long) : SharedLimiter(limit, windowLength)
