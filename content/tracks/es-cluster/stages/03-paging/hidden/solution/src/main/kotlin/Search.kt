private const val MAX_RESULT_WINDOW = 10_000

class Hit(val document: Document, val shardNumber: Int, val score: RoundedScore)

class RankedShard(private val order: SortOrder, val hits: List<Hit>) {

    fun hitsAfter(searchAfter: List<Long>?): List<Hit> {
        if (searchAfter == null) return hits
        var low = 0
        var high = hits.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (order.isAfter(hits[middle], searchAfter)) high = middle else low = middle + 1
        }
        return hits.subList(low, hits.size)
    }
}

class SearchResponse(val totalHits: Int, val mergedHits: Int, val hits: List<Hit>, val sortKeys: List<SortKey>)

fun search(index: ShardedIndex, request: SearchRequest): SearchResponse {
    val pointInTime = request.pointInTimeId?.let(index::findPointInTime)
    if (request.from + request.size > MAX_RESULT_WINDOW) reject(ErrorCode.RESULT_WINDOW)
    val order = SortOrder(request.sortKeys)
    val ranking = if (pointInTime == null) {
        rank(index.shards, request, order)
    } else {
        pointInTime.ranking(RankingKey(request.query, request.searchType, request.sortKeys)) {
            rank(pointInTime.shards, request, order)
        }
    }
    return page(ranking, request, order)
}

private fun rank(shards: List<ShardReader>, request: SearchRequest, order: SortOrder): List<RankedShard> {
    val hitsByShard = when (val query = request.query) {
        Query.MatchAll -> shards.map { shard -> shard.documents.map { Hit(it, shard.number, RoundedScore.ONE) } }
        is Query.Terms -> {
            val statistics = scoringStatistics(shards, query.tokens, request.searchType)
            shards.zip(statistics) { shard, shardStatistics -> scoredHits(shard, query.tokens, shardStatistics) }
        }
    }
    return hitsByShard.map { RankedShard(order, it.sortedWith(order)) }
}

private fun scoringStatistics(shards: List<ShardReader>, tokens: List<String>, searchType: SearchType): List<CorpusStatistics> {
    val shardStatistics = shards.map { CorpusStatistics.of(it, tokens) }
    return when (searchType) {
        SearchType.QUERY_THEN_FETCH -> shardStatistics
        SearchType.DFS_QUERY_THEN_FETCH -> {
            val globalStatistics = CorpusStatistics.combine(shardStatistics)
            shardStatistics.map { globalStatistics }
        }
    }
}

private fun scoredHits(shard: ShardReader, tokens: List<String>, statistics: CorpusStatistics): List<Hit> {
    val scorer = Bm25Scorer(tokens, statistics)
    return shard.documentsContainingAny(tokens).map { Hit(it, shard.number, RoundedScore.of(scorer.score(it))) }
}

private fun page(ranking: List<RankedShard>, request: SearchRequest, order: SortOrder): SearchResponse {
    val window = request.from + request.size
    val returnedHits = ranking.flatMap { it.hitsAfter(request.searchAfter).take(window) }
    return SearchResponse(
        totalHits = ranking.sumOf { it.hits.size },
        mergedHits = returnedHits.size,
        hits = returnedHits.sortedWith(order).drop(request.from).take(request.size),
        sortKeys = request.sortKeys,
    )
}
