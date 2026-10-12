class RequestTimeLog(private val windowLength: Long) {
    private val times = ArrayDeque<Long>()

    val count: Long
        get() = times.size.toLong()

    fun record(time: Long) {
        times.addLast(time)
        while (times.first() <= time - windowLength) times.removeFirst()
    }
}
