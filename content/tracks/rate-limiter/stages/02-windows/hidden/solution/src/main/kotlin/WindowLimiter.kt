class WindowLimiter(private val limit: Long, private val windowLength: Long) : Limiter {
    private val keyCounters = HashMap<String, KeyCounters>()
    private val storage = StorageMeter(windowLength)
    val report = WindowReport()

    override fun request(key: String, time: Long): WindowVerdict {
        val counters = keyCounters.getOrPut(key) {
            KeyCounters(FixedWindowCounter(windowLength), RequestTimeLog(windowLength))
        }
        counters.fixedWindow.record(time)
        counters.timeLog.record(time)
        val stored = storage.record(time, keyIsNewInWindow = counters.fixedWindow.count == 1L)
        val verdict = WindowVerdict(
            fixed = judge(counters.fixedWindow.count),
            log = judge(counters.timeLog.count),
            sliding = judge(counters.fixedWindow.slidingEstimate(time)),
        )
        report.add(verdict, stored)
        return verdict
    }

    private fun judge(count: Long) = MethodVerdict(allowed = count <= limit, count)

    private class KeyCounters(val fixedWindow: FixedWindowCounter, val timeLog: RequestTimeLog)
}
