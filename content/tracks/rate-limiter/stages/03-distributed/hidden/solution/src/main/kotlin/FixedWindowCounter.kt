class FixedWindowCounter(private val windowLength: Long) {
    private var window = 0L
    var previousWindowCount = 0L
        private set
    var count = 0L
        private set

    fun record(time: Long) {
        val requestWindow = time / windowLength
        if (requestWindow != window) {
            previousWindowCount = if (requestWindow == window + 1) count else 0
            count = 0
            window = requestWindow
        }
        count++
    }

    // floor(p × (W − e) ÷ W) + c. 모든 값이 0 이상이라 정수 나눗셈이 곧 floor 다
    fun slidingEstimate(time: Long): Long {
        val elapsed = time - window * windowLength
        return previousWindowCount * (windowLength - elapsed) / windowLength + count
    }
}
