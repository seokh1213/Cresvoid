class Hit(val document: Document, val shardNumber: Int, val score: RoundedScore)

class ShardReply(val matchCount: Int, val topHits: List<Hit>)

class SearchResponse(val totalHits: Int, val mergedHits: Int, val hits: List<Hit>)

private val HIT_ORDER: Comparator<Hit> = compareByDescending<Hit> { it.score }
    .thenBy { it.shardNumber }
    .thenBy { it.document.internalNumber }

fun search(index: ShardedIndex, request: SearchRequest): SearchResponse {
    val replies = when (val query = request.query) {
        Query.MatchAll -> index.shards.map { shard -> matchAllReply(shard, request.size) }
        is Query.Terms -> {
            val statistics = scoringStatistics(index.shards, query.tokens, request.searchType)
            index.shards.zip(statistics) { shard, shardStatistics -> termsReply(shard, query.tokens, shardStatistics, request.size) }
        }
    }
    val returnedHits = replies.flatMap { it.topHits }
    return SearchResponse(
        totalHits = replies.sumOf { it.matchCount },
        mergedHits = returnedHits.size,
        hits = returnedHits.sortedWith(HIT_ORDER).take(request.size),
    )
}

private fun scoringStatistics(shards: List<Shard>, tokens: List<String>, searchType: SearchType): List<CorpusStatistics> {
    val shardStatistics = shards.map { CorpusStatistics.of(it, tokens) }
    return when (searchType) {
        SearchType.QUERY_THEN_FETCH -> shardStatistics
        SearchType.DFS_QUERY_THEN_FETCH -> {
            val globalStatistics = CorpusStatistics.combine(shardStatistics)
            shardStatistics.map { globalStatistics }
        }
    }
}

private fun matchAllReply(shard: Shard, size: Int): ShardReply =
    reply(shard.documents.map { Hit(it, shard.number, RoundedScore.ONE) }, size)

private fun termsReply(shard: Shard, tokens: List<String>, statistics: CorpusStatistics, size: Int): ShardReply {
    val scorer = Bm25Scorer(tokens, statistics)
    val hits = shard.documentsContainingAny(tokens).map { Hit(it, shard.number, RoundedScore.of(scorer.score(it))) }
    return reply(hits, size)
}

private fun reply(hits: List<Hit>, size: Int): ShardReply = ShardReply(hits.size, hits.sortedWith(HIT_ORDER).take(size))
