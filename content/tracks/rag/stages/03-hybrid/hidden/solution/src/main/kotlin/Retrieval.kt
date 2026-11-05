import kotlin.math.ln
import kotlin.math.sqrt

data class SearchHit(val chunk: Chunk, val units: Long)
val hitOrder: Comparator<SearchHit> = compareByDescending<SearchHit> { it.units }.thenBy { it.chunk.id }

fun tokenize(text: String): List<String> = buildList {
    val token = StringBuilder()
    var previous = 0
    for (character in text) {
        val category = when (character) {
            in 'A'..'Z', in 'a'..'z', in '0'..'9' -> 1
            in '\uAC00'..'\uD7A3' -> 2
            else -> 0
        }
        if (category != previous || category == 0) {
            if (token.isNotEmpty()) add(token.toString())
            token.setLength(0)
        }
        if (category != 0) token.append(if (character in 'A'..'Z') character + 32 else character)
        previous = category
    }
    if (token.isNotEmpty()) add(token.toString())
}

class LexicalIndex(private val chunks: List<Chunk>) {
    private data class Posting(val position: Int, val frequency: Int)
    private val lengths = IntArray(chunks.size)
    private val postings = HashMap<String, MutableList<Posting>>()
    private val averageLength: Double

    init {
        for ((position, chunk) in chunks.withIndex()) {
            val tokens = tokenize(chunk.text)
            lengths[position] = tokens.size
            for ((token, frequency) in tokens.groupingBy { it }.eachCount()) {
                postings.getOrPut(token) { mutableListOf() }.add(Posting(position, frequency))
            }
        }
        averageLength = if (chunks.isEmpty()) 0.0 else lengths.sum().toDouble() / chunks.size
    }

    fun search(text: String): List<SearchHit> {
        val scores = DoubleArray(chunks.size)
        val candidate = BooleanArray(chunks.size)
        for (token in tokenize(text).toSortedSet()) {
            val occurrences = postings[token] ?: continue
            val idf = ln(1 + (chunks.size - occurrences.size + 0.5) / (occurrences.size + 0.5))
            for (posting in occurrences) {
                val position = posting.position
                val frequency = posting.frequency.toDouble()
                scores[position] += idf * frequency * 2.2 /
                    (frequency + 1.2 * (1 - 0.75 + 0.75 * lengths[position] / averageLength))
                candidate[position] = true
            }
        }
        return chunks.indices.filter { candidate[it] }
            .map { SearchHit(chunks[it], roundedUnits(scores[it], 4)) }.sortedWith(hitOrder)
    }
}

class Embeddings {
    private val vectors = HashMap<ChunkId, DoubleArray>()
    var dimension: Int? = null
        private set

    fun clear() {
        vectors.clear()
        dimension = null
    }

    fun checkDimension(vector: DoubleArray) {
        if (dimension != null && dimension != vector.size) fail("DIMENSION")
    }

    fun put(id: ChunkId, vector: DoubleArray) {
        checkDimension(vector)
        vectors[id] = vector
        dimension = vector.size
    }

    fun search(chunks: List<Chunk>, vector: DoubleArray): List<SearchHit> = chunks.mapNotNull { chunk ->
        vectors[chunk.id]?.let { SearchHit(chunk, cosineUnits(it, vector)) }
    }.sortedWith(hitOrder)

    fun similarity(first: ChunkId, second: ChunkId): Double {
        val left = vectors[first] ?: return 0.0
        val right = vectors[second] ?: return 0.0
        return cosineUnits(left, right) / 1_000_000.0
    }
}

fun cosineUnits(left: DoubleArray, right: DoubleArray): Long {
    var product = 0.0
    var leftSquare = 0.0
    var rightSquare = 0.0
    for (index in left.indices) {
        product += left[index] * right[index]
        leftSquare += left[index] * left[index]
        rightSquare += right[index] * right[index]
    }
    if (leftSquare == 0.0 || rightSquare == 0.0) return 0L
    val similarity = product / (sqrt(leftSquare) * sqrt(rightSquare))
    return roundedUnits(similarity.coerceIn(-1.0, 1.0), 6)
}
