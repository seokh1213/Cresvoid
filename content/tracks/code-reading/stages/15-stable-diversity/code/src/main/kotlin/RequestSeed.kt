data class RecommendationRequest(
    val sessionId: String,
    val page: Int,
    val count: Int,
    val qualityPercent: Int,
) {
    init {
        require(sessionId.matches(Regex("[A-Za-z0-9_-]{1,32}")))
        require(page in 0..99)
        require(count in 0..100)
        require(qualityPercent in 0..100)
    }
}

object RequestSeed {
    private const val SESSION_BUCKETS = 1009L
    private const val PAGE_SLOTS = 100L

    fun forRequest(request: RecommendationRequest): Long {
        val sessionBucket = request.sessionId.fold(0L) { bucket, character ->
            (bucket * 31 + character.code) % SESSION_BUCKETS
        }
        return sessionBucket * PAGE_SLOTS + request.page
    }
}
