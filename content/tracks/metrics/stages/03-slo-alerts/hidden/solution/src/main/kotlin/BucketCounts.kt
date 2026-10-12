import java.math.BigDecimal

private const val INFINITE_BOUND = "+Inf"

class BucketCounts(val upperBounds: List<Long>, val counts: List<BigDecimal>) {
    fun line(): String = counts.withIndex().joinToString(" ") { (bucket, count) ->
        val bound = upperBounds.getOrNull(bucket)?.toString() ?: INFINITE_BOUND
        "$bound:${formatDecimal(count)}"
    }

    companion object {
        fun combine(upperBounds: List<Long>, increasesPerSeries: List<List<BigDecimal>>): BucketCounts? {
            if (increasesPerSeries.isEmpty()) return null
            val counts = increasesPerSeries.reduce { combined, increases -> combined.zip(increases, BigDecimal::add) }
            return BucketCounts(upperBounds, counts)
        }
    }
}
