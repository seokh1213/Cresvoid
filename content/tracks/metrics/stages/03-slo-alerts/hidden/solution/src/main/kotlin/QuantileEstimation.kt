import java.math.BigDecimal

fun estimateQuantile(level: BigDecimal, buckets: BucketCounts): BigDecimal? {
    val counts = buckets.counts.runningReduce { countBelow, count -> count.max(countBelow) }
    val total = counts.last()
    if (total.signum() == 0) return null
    val rank = level * total
    val upperBounds = buckets.upperBounds
    val bucket = upperBounds.indices.firstOrNull { counts[it] >= rank } ?: return BigDecimal.valueOf(upperBounds.last())
    val lowerBound = if (bucket == 0) BigDecimal.ZERO else BigDecimal.valueOf(upperBounds[bucket - 1])
    val countBelow = if (bucket == 0) BigDecimal.ZERO else counts[bucket - 1]
    val upperBound = BigDecimal.valueOf(upperBounds[bucket])
    // 아래 경계는 정수라서 버킷 안 몫만 반올림해 더해도 전체를 반올림한 값과 같다
    return lowerBound + roundedQuotient((upperBound - lowerBound) * (rank - countBelow), counts[bucket] - countBelow)
}
