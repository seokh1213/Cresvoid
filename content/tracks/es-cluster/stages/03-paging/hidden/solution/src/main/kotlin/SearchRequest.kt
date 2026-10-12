private val SIZE_FORMAT = Regex("0|[1-9][0-9]*")
private val FROM_FORMAT = Regex("0|[1-9][0-9]{0,8}")
private val SCORE_VALUE_FORMAT = Regex("(0|[1-9][0-9]{0,8})\\.[0-9]{4}")
private val INTEGER_VALUE_FORMAT = Regex("0|[1-9][0-9]{0,17}")
private const val MAX_SIZE = 10_000
private const val DEFAULT_SIZE = 10
private val DEFAULT_SORT = listOf(SortKey(SortField.SCORE, SortDirection.DESC))
private val PIT_TIEBREAKER = SortKey(SortField.SHARD_DOC, SortDirection.ASC)
private val PARAMETER_NAMES = setOf("q", "size", "search_type", "from", "sort", "search_after", "pit")

sealed interface Query {
    data object MatchAll : Query

    data class Terms(val tokens: List<String>) : Query
}

enum class SearchType(val parameterValue: String) {
    QUERY_THEN_FETCH("query_then_fetch"),
    DFS_QUERY_THEN_FETCH("dfs_query_then_fetch"),
    ;

    companion object {
        fun find(parameterValue: String): SearchType? = entries.firstOrNull { it.parameterValue == parameterValue }
    }
}

data class SearchRequest(
    val query: Query,
    val size: Int,
    val searchType: SearchType,
    val from: Int,
    val sortKeys: List<SortKey>,
    val searchAfter: List<Long>?,
    val pointInTimeId: Int?,
)

fun parseSearchRequest(arguments: List<String>): SearchRequest {
    val parameters = parseParameters(arguments, PARAMETER_NAMES)
    val from = parameters["from"]?.let { it.takeIf(FROM_FORMAT::matches)?.toInt() ?: reject(ErrorCode.BAD_ARGUMENT) } ?: 0
    val pointInTimeId = parameters["pit"]?.let(::parsePointInTimeId)
    val sortKeys = actualSortKeys(parameters["sort"]?.let(::parseSortKeys) ?: DEFAULT_SORT, pointInTimeId != null)
    val searchAfter = parameters["search_after"]?.let { parseSearchAfter(it, sortKeys) }
    if (searchAfter != null && from != 0) reject(ErrorCode.BAD_ARGUMENT)
    return SearchRequest(
        query = parameters["q"]?.let { Query.Terms(tokenize(it).distinct()) } ?: Query.MatchAll,
        size = parameters["size"]?.let { parseBoundedInt(it, SIZE_FORMAT, MAX_SIZE) } ?: DEFAULT_SIZE,
        searchType = parameters["search_type"]?.let { SearchType.find(it) ?: reject(ErrorCode.BAD_ARGUMENT) }
            ?: SearchType.QUERY_THEN_FETCH,
        from = from,
        sortKeys = sortKeys,
        searchAfter = searchAfter,
        pointInTimeId = pointInTimeId,
    )
}

private fun parseSortKeys(value: String): List<SortKey> {
    val keys = value.split(',').map(::parseSortKey)
    if (keys.distinctBy { it.field }.size != keys.size) reject(ErrorCode.BAD_ARGUMENT)
    return keys
}

private fun parseSortKey(text: String): SortKey {
    val field = SortField.find(text.substringBefore(':')) ?: reject(ErrorCode.BAD_ARGUMENT)
    if (':' !in text) return SortKey(field, field.defaultDirection)
    val direction = SortDirection.find(text.substringAfter(':')) ?: reject(ErrorCode.BAD_ARGUMENT)
    return SortKey(field, direction)
}

private fun actualSortKeys(requested: List<SortKey>, withPointInTime: Boolean): List<SortKey> {
    val mentionsShardDoc = requested.any { it.field == SortField.SHARD_DOC }
    return when {
        mentionsShardDoc && !withPointInTime -> reject(ErrorCode.BAD_ARGUMENT)
        !mentionsShardDoc && withPointInTime -> requested + PIT_TIEBREAKER
        else -> requested
    }
}

private fun parseSearchAfter(value: String, sortKeys: List<SortKey>): List<Long> {
    val pieces = value.split(',')
    if (pieces.size != sortKeys.size) reject(ErrorCode.BAD_ARGUMENT)
    return pieces.zip(sortKeys) { piece, key -> parseSortValue(piece, key.field) }
}

private fun parseSortValue(text: String, field: SortField): Long =
    when {
        field == SortField.SCORE && SCORE_VALUE_FORMAT.matches(text) -> RoundedScore.parse(text).tenThousandths
        field != SortField.SCORE && INTEGER_VALUE_FORMAT.matches(text) -> text.toLong()
        else -> reject(ErrorCode.BAD_ARGUMENT)
    }
