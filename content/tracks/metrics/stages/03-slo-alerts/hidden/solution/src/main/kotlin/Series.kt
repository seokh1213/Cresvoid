sealed class Series(val labels: Set<LabelPair>) {
    protected val times = LongColumn()

    val lastTime: Long get() = times.last

    protected fun samplesIn(window: RangeWindow): SamplesInRange? {
        val first = times.countAtMost(window.start)
        val last = times.countAtMost(window.end) - 1
        val count = last - first + 1
        if (count < 2) return null
        return SamplesInRange(first, last, Extrapolation.of(window, times[first], times[last], count))
    }
}

class SamplesInRange(val first: Int, val last: Int, val extrapolation: Extrapolation)
