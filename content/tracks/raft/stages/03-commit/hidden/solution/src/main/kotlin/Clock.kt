class Clock {
    var now: Long = 0
        private set

    fun advance() {
        now++
    }
}
