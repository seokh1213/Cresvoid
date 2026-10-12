class Clock {
    var now: Long = 0
        private set

    fun advanceTo(time: Long) {
        now = time
    }
}
