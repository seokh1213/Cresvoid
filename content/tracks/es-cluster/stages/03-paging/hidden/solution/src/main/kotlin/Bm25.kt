import kotlin.math.ln

private const val K1 = 1.2
private const val B = 0.75

class CorpusStatistics(
    val documentCount: Int,
    val tokenCount: Long,
    private val documentFrequencies: Map<String, Int>,
) {
    val averageLength: Double = tokenCount.toDouble() / documentCount

    fun inverseDocumentFrequency(token: String): Double {
        val documentFrequency = documentFrequencies.getValue(token)
        return ln(1 + (documentCount - documentFrequency + 0.5) / (documentFrequency + 0.5))
    }

    companion object {
        fun of(shard: ShardReader, tokens: List<String>): CorpusStatistics =
            CorpusStatistics(shard.documentCount, shard.tokenCount, tokens.associateWith(shard::documentFrequency))

        fun combine(parts: List<CorpusStatistics>): CorpusStatistics =
            CorpusStatistics(
                documentCount = parts.sumOf { it.documentCount },
                tokenCount = parts.sumOf { it.tokenCount },
                documentFrequencies = parts.first().documentFrequencies.keys.associateWith { token ->
                    parts.sumOf { it.documentFrequencies.getValue(token) }
                },
            )
    }
}

private class QueryTerm(val token: String, val inverseDocumentFrequency: Double)

class Bm25Scorer(tokens: List<String>, statistics: CorpusStatistics) {
    private val terms = tokens.map { QueryTerm(it, statistics.inverseDocumentFrequency(it)) }
    private val averageLength = statistics.averageLength

    fun score(document: Document): Double =
        terms.sumOf { term ->
            val termFrequency = document.termFrequencies[term.token] ?: 0
            term.inverseDocumentFrequency * termFrequency * (K1 + 1) /
                (termFrequency + K1 * (1 - B + B * document.length / averageLength))
        }
}
