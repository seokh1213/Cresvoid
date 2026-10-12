data class StoredCounts(val log: Long, val fixed: Long, val sliding: Long) {
    fun peakWith(other: StoredCounts) = StoredCounts(
        log = maxOf(log, other.log),
        fixed = maxOf(fixed, other.fixed),
        sliding = maxOf(sliding, other.sliding),
    )
}

class StorageMeter(windowLength: Long) {
    private val requestTimes = RequestTimeLog(windowLength)
    private val keysPerWindow = FixedWindowCounter(windowLength)

    fun record(time: Long, keyIsNewInWindow: Boolean): StoredCounts {
        requestTimes.record(time)
        // 같은 창에서 키의 두 번째 요청이면 그 키의 첫 요청이 keysPerWindow 를 이미 이 창으로 옮겨 두었다
        if (keyIsNewInWindow) keysPerWindow.record(time)
        return StoredCounts(
            log = requestTimes.count,
            fixed = keysPerWindow.count,
            sliding = keysPerWindow.count + keysPerWindow.previousWindowCount,
        )
    }
}
