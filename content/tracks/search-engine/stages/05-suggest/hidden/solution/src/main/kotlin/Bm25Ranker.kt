import kotlin.math.ln

private const val K1 = 1.2
private const val B = 0.75

private val BY_SCORE_THEN_ID = compareByDescending<RankedDocument> { it.score }.thenBy { it.id }

data class RankedDocument(val id: DocumentId, val score: RoundedScore)

class Bm25Ranker(private val index: InvertedIndex) {

    fun rank(query: SearchQuery, limit: Int): List<RankedDocument> {
        val candidates = findCandidates(query)
        if (candidates.isEmpty()) return emptyList()
        // 낱말을 쓴 순서가 달라도 같은 점수가 나오도록 몫을 더하는 순서를 고정한다
        val idfByToken = query.scoringTokens.sorted().associateWith(::inverseDocumentFrequency)
        val averageLength = index.averageDocumentLength
        return candidates
            .map { id -> RankedDocument(id, RoundedScore.of(score(id, idfByToken, averageLength))) }
            .sortedWith(BY_SCORE_THEN_ID)
            .take(limit)
    }

    private fun findCandidates(query: SearchQuery): List<DocumentId> {
        val requiredOverlapsExcluded = query.required.any { it in query.excluded }
        if (requiredOverlapsExcluded || query.scoringTokens.isEmpty()) return emptyList()
        val included = if (query.required.isEmpty()) {
            documentsContainingAny(query.scoringTokens)
        } else {
            documentsContainingAll(query.required)
        }
        val excludedPostings = query.excluded.map(index::postings)
        return included.filter { id -> excludedPostings.none { id in it } }
    }

    private fun documentsContainingAll(tokens: Set<String>): List<DocumentId> {
        val postingsBySize = tokens.map(index::postings).sortedBy { it.size }
        return postingsBySize.first().filter { id -> postingsBySize.all { id in it } }
    }

    private fun documentsContainingAny(tokens: Set<String>): Set<DocumentId> =
        tokens.flatMapTo(HashSet(), index::postings)

    private fun inverseDocumentFrequency(token: String): Double {
        val documentCount = index.documentCount
        val documentFrequency = index.documentFrequency(token)
        return ln(1 + (documentCount - documentFrequency + 0.5) / (documentFrequency + 0.5))
    }

    private fun score(id: DocumentId, idfByToken: Map<String, Double>, averageLength: Double): Double {
        val length = index.documentLength(id)
        return idfByToken.entries.sumOf { (token, idf) ->
            val termFrequency = index.termFrequency(id, token)
            idf * termFrequency * (K1 + 1) / (termFrequency + K1 * (1 - B + B * length / averageLength))
        }
    }
}
