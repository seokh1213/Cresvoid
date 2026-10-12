import java.math.BigDecimal

class GaugeSeries(labels: Set<LabelPair>) : Series(labels) {
    private val values = LongColumn()

    fun append(time: Long, value: Long) {
        times.append(time)
        values.append(value)
    }

    fun delta(window: RangeWindow): BigDecimal? {
        val samples = samplesIn(window) ?: return null
        return samples.extrapolation.extrapolate(values[samples.last] - values[samples.first])
    }
}
