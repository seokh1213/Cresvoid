class CounterColumn {
    private val rawIncreaseSinceFirst = LongColumn()
    private var lastValue = 0L

    fun append(value: Long) {
        val isFirst = rawIncreaseSinceFirst.size == 0
        rawIncreaseSinceFirst.append(if (isFirst) 0L else rawIncreaseSinceFirst.last + increaseFrom(lastValue, value))
        lastValue = value
    }

    fun rawIncrease(first: Int, last: Int): Long = rawIncreaseSinceFirst[last] - rawIncreaseSinceFirst[first]
}

private fun increaseFrom(previous: Long, next: Long): Long {
    val restarted = next < previous
    return if (restarted) next else next - previous
}
