import java.math.BigDecimal

class CounterSeries(labels: Set<LabelPair>) : Series(labels) {
    private val values = CounterColumn()

    fun append(time: Long, value: Long) {
        times.append(time)
        values.append(value)
    }

    fun increase(window: RangeWindow): BigDecimal? {
        val samples = samplesIn(window) ?: return null
        return samples.extrapolation.extrapolate(values.rawIncrease(samples.first, samples.last))
    }
}
