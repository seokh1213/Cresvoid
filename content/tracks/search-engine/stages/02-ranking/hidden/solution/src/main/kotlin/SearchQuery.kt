data class SearchQuery(
    val required: Set<String>,
    val excluded: Set<String>,
    val optional: Set<String>,
) {
    val scoringTokens: Set<String> = (required + optional) - excluded
}

fun parseSearchQuery(words: List<String>): SearchQuery {
    val required = LinkedHashSet<String>()
    val excluded = LinkedHashSet<String>()
    val optional = LinkedHashSet<String>()
    for (word in words) {
        when (word.first()) {
            '+' -> required += tokenize(word.drop(1))
            '-' -> excluded += tokenize(word.drop(1))
            else -> optional += tokenize(word)
        }
    }
    return SearchQuery(required = required, excluded = excluded, optional = optional - required)
}
