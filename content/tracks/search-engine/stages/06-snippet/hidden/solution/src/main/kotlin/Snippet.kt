private const val MAX_SPAN_TOKENS = 10

private data class Occurrence(val position: Int, val token: String)

private data class TokenSpan(val first: Int, val last: Int) {
    val tokenCount: Int get() = last - first + 1
}

private data class SpanCandidate(val span: TokenSpan, val distinctQueryTokens: Int)

private val BY_DISTINCT_THEN_SHORTER_THEN_EARLIER =
    compareByDescending<SpanCandidate> { it.distinctQueryTokens }
        .thenBy { it.span.tokenCount }
        .thenBy { it.span.first }

fun buildSnippet(document: IndexedDocument, queryTokens: Set<String>): String? {
    val occurrences = queryTokens
        .flatMap { token -> document.positions(token).map { position -> Occurrence(position, token) } }
        .sortedBy { it.position }
    if (occurrences.isEmpty()) return null
    return highlight(document, densestSpan(occurrences), queryTokens)
}

private fun densestSpan(occurrences: List<Occurrence>): TokenSpan {
    val countInWindow = HashMap<String, Int>()
    val lastIndexByToken = HashMap<String, Int>()
    var best: SpanCandidate? = null
    var left = 0
    for ((right, occurrence) in occurrences.withIndex()) {
        countInWindow[occurrence.token] = countInWindow.getOrDefault(occurrence.token, 0) + 1
        lastIndexByToken[occurrence.token] = right
        while (occurrence.position - occurrences[left].position + 1 > MAX_SPAN_TOKENS) {
            val leaving = occurrences[left].token
            val remaining = countInWindow.getValue(leaving) - 1
            if (remaining == 0) countInWindow.remove(leaving) else countInWindow[leaving] = remaining
            left++
        }
        // right 에서 끝나며 창 안의 검색어 종류를 모두 담는 가장 짧은 구간은 검색어마다 마지막 출현 가운데 가장 앞선 곳에서 시작한다
        val start = countInWindow.keys.minOf { lastIndexByToken.getValue(it) }
        val candidate = SpanCandidate(TokenSpan(occurrences[start].position, occurrence.position), countInWindow.size)
        if (best == null || BY_DISTINCT_THEN_SHORTER_THEN_EARLIER.compare(candidate, best) < 0) best = candidate
    }
    return checkNotNull(best).span
}

private fun highlight(document: IndexedDocument, span: TokenSpan, queryTokens: Set<String>): String = buildString {
    val source = document.source
    var copiedUntil = document.sourceToken(span.first).start
    for (position in span.first..span.last) {
        val sourceToken = document.sourceToken(position)
        if (sourceToken.token !in queryTokens) continue
        append(source, copiedUntil, sourceToken.start)
        append('[').append(source, sourceToken.start, sourceToken.endExclusive).append(']')
        copiedUntil = sourceToken.endExclusive
    }
}
