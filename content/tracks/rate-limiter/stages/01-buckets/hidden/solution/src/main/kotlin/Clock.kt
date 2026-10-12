class Clock {
    var now: Long = 0
        private set

    fun advanceTo(timeArgument: Long): Long {
        now = maxOf(now, timeArgument)
        return now
    }
}
