class Gateway(val delay: Long, private val workers: Long, val processingTime: Long) {
    private var busyWorkers = 0L
    private val copies = HashMap<CopySlot, LocalCopy>()

    fun mustShed(priority: Priority): Boolean = busyWorkers * 100 >= workers * priority.busyCapPercent

    fun takeWorker() {
        busyWorkers++
    }

    fun returnWorker() {
        busyWorkers--
    }

    fun copyFor(counter: CounterKey): LocalCopy {
        val slot = CopySlot(counter.limiter, counter.key)
        val current = copies[slot]
        if (current != null && current.counter.window == counter.window) return current
        return LocalCopy(this, counter).also { copies[slot] = it }
    }

    fun holdsCopyOfWindow(counter: CounterKey): Boolean =
        copies[CopySlot(counter.limiter, counter.key)]?.counter?.window == counter.window

    private data class CopySlot(val limiter: SharedLimiter, val key: String)
}
