import java.math.BigDecimal

class HistogramSeries(labels: Set<LabelPair>, bucketCount: Int) : Series(labels) {
    private val buckets = List(bucketCount) { CounterColumn() }

    fun append(time: Long, counts: List<Long>) {
        times.append(time)
        buckets.forEachIndexed { index, bucket -> bucket.append(counts[index]) }
    }

    fun bucketIncreases(window: RangeWindow): List<BigDecimal>? {
        val samples = samplesIn(window) ?: return null
        return buckets.map { bucket ->
            samples.extrapolation.extrapolate(bucket.rawIncrease(samples.first, samples.last))
        }
    }
}
