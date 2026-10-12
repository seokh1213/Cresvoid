private val SIZE_FORMAT = Regex("0|[1-9][0-9]*")
private const val MAX_SIZE = 10_000
private const val DEFAULT_SIZE = 10
private val PARAMETER_NAMES = setOf("q", "size", "search_type")

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

data class SearchRequest(val query: Query, val size: Int, val searchType: SearchType)

fun parseSearchRequest(arguments: List<String>): SearchRequest {
    val parameters = parseParameters(arguments, PARAMETER_NAMES)
    return SearchRequest(
        query = parameters["q"]?.let { Query.Terms(tokenize(it).distinct()) } ?: Query.MatchAll,
        size = parameters["size"]?.let { parseBoundedInt(it, SIZE_FORMAT, MAX_SIZE) } ?: DEFAULT_SIZE,
        searchType = parameters["search_type"]?.let { SearchType.find(it) ?: reject(ErrorCode.BAD_ARGUMENT) }
            ?: SearchType.QUERY_THEN_FETCH,
    )
}
