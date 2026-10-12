import kotlin.math.abs

class IndexedDocument(val source: String) {
    private val sourceTokens: List<SourceToken> = tokenizeSource(source)
    private val tokens: List<String> = sourceTokens.map { it.token }
    private val positionsByToken: Map<String, List<Int>> = tokens.indices.groupBy { position -> tokens[position] }

    val length: Int get() = tokens.size

    val distinctTokens: Set<String> get() = positionsByToken.keys

    fun termFrequency(token: String): Int = positions(token).size

    fun positions(token: String): List<Int> = positionsByToken[token].orEmpty()

    fun sourceToken(position: Int): SourceToken = sourceTokens[position]

    fun containsPhrase(phrase: List<String>): Boolean =
        positions(phrase.first()).any { start ->
            val end = start + phrase.size
            end <= tokens.size && tokens.subList(start, end) == phrase
        }

    fun containsNear(left: String, right: String, maxDistance: Int): Boolean {
        if (left == right) {
            return positions(left).zipWithNext().any { (earlier, later) -> later - earlier <= maxDistance }
        }
        return hasPairWithin(positions(left), positions(right), maxDistance)
    }
}

private fun hasPairWithin(leftPositions: List<Int>, rightPositions: List<Int>, maxDistance: Int): Boolean {
    var leftIndex = 0
    var rightIndex = 0
    while (leftIndex < leftPositions.size && rightIndex < rightPositions.size) {
        val leftPosition = leftPositions[leftIndex]
        val rightPosition = rightPositions[rightIndex]
        if (abs(leftPosition - rightPosition) <= maxDistance) return true
        if (leftPosition < rightPosition) leftIndex++ else rightIndex++
    }
    return false
}
