class ShardBuckets(val buckets: List<BrandBucket>, val errorShare: Int) {
    private val sentBrands: Set<String> = buckets.mapTo(HashSet()) { it.brand }

    fun sent(brand: String): Boolean = brand in sentBrands
}

class ApproximateBucket(val brand: String, val count: Int, val errorBound: Int, val exactCount: Int)

class TermsResponse(
    val approximateBuckets: List<ApproximateBucket>,
    val errorBound: Int,
    val approximateOtherCount: Int,
    val exactBuckets: List<BrandBucket>,
    val exactOtherCount: Int,
)

fun aggregateTerms(index: ShardedIndex, request: TermsRequest): TermsResponse {
    val shardReplies = index.shards.map { shardBuckets(it, request.shardSize) }
    val approximate = mergeBuckets(shardReplies).sortedWith(BUCKET_ORDER).take(request.size)
    val exact = index.exactBrandCounts.top(request.size)
    return TermsResponse(
        approximateBuckets = approximate.map { bucket ->
            ApproximateBucket(
                brand = bucket.brand,
                count = bucket.count,
                errorBound = shardReplies.filterNot { it.sent(bucket.brand) }.sumOf { it.errorShare },
                exactCount = index.exactBrandCounts.count(bucket.brand),
            )
        },
        errorBound = if (index.shards.size == 1) 0 else shardReplies.sumOf { it.errorShare },
        approximateOtherCount = index.documentCount - approximate.sumOf { it.count },
        exactBuckets = exact,
        exactOtherCount = index.documentCount - exact.sumOf { it.count },
    )
}

private fun shardBuckets(shard: Shard, shardSize: Int): ShardBuckets {
    val buckets = shard.brandCounts.top(shardSize)
    val errorShare = if (buckets.size < shardSize) 0 else buckets.last().count
    return ShardBuckets(buckets, errorShare)
}

private fun mergeBuckets(replies: List<ShardBuckets>): List<BrandBucket> {
    val countByBrand = HashMap<String, Int>()
    for (reply in replies) {
        for (bucket in reply.buckets) countByBrand.merge(bucket.brand, bucket.count, Int::plus)
    }
    return countByBrand.map { (brand, count) -> BrandBucket(brand, count) }
}
