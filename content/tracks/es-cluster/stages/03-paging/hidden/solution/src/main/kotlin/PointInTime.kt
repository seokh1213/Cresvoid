private val POINT_IN_TIME_ID_FORMAT = Regex("[1-9][0-9]{0,8}")

data class RankingKey(val query: Query, val searchType: SearchType, val sortKeys: List<SortKey>)

class PointInTime(val id: Int, val shards: List<FrozenShard>) {
    private val rankings = HashMap<RankingKey, List<RankedShard>>()

    fun ranking(key: RankingKey, rank: () -> List<RankedShard>): List<RankedShard> = rankings.getOrPut(key, rank)
}

fun parsePointInTimeId(text: String): Int =
    text.takeIf(POINT_IN_TIME_ID_FORMAT::matches)?.toInt() ?: reject(ErrorCode.BAD_ARGUMENT)
