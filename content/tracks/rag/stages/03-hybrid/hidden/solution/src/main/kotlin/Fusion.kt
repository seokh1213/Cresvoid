data class FusionQuery(val depth: Int, val constant: Int, val text: String, val vector: DoubleArray)

fun fuse(lexical: List<SearchHit>, semantic: List<SearchHit>, constant: Int): List<SearchHit> {
    val scores = HashMap<ChunkId, Double>()
    val candidates = HashMap<ChunkId, Chunk>()
    for (ranking in listOf(lexical, semantic)) {
        for ((index, hit) in ranking.withIndex()) {
            val id = hit.chunk.id
            scores[id] = (scores[id] ?: 0.0) + 1.0 / (constant + index + 1)
            candidates[id] = hit.chunk
        }
    }
    return candidates.map { (id, chunk) -> SearchHit(chunk, roundedUnits(scores.getValue(id), 6)) }
        .sortedWith(hitOrder)
}

fun diversify(candidates: List<SearchHit>, embeddings: Embeddings, k: Int, lambda: Double): List<SearchHit> {
    if (candidates.isEmpty()) return emptyList()
    val maximum = candidates.first().units / 1_000_000.0
    val remaining = candidates.toMutableList()
    val redundancy = HashMap<ChunkId, Double>()
    return buildList {
        repeat(minOf(k, candidates.size)) {
            val selected = remaining.map { hit ->
                val relevance = (hit.units / 1_000_000.0) / maximum
                val penalty = redundancy[hit.chunk.id] ?: 0.0
                SearchHit(hit.chunk, roundedUnits(lambda * relevance - (1.0 - lambda) * penalty, 6))
            }.minWith(hitOrder)
            add(selected)
            remaining.removeAll { it.chunk.id == selected.chunk.id }
            for (hit in remaining) {
                val id = hit.chunk.id
                redundancy[id] = maxOf(redundancy[id] ?: 0.0,
                    embeddings.similarity(id, selected.chunk.id))
            }
        }
    }
}

fun groupDocuments(candidates: List<SearchHit>, k: Int): List<String> =
    candidates.groupBy { it.chunk.id.document }.entries
        .sortedWith(compareByDescending<Map.Entry<Int, List<SearchHit>>> { it.value.first().units }
            .thenBy { it.key })
        .take(k).map { (id, hits) ->
            "$id\t${fixedUnits(hits.first().units, 6)}\t${hits.joinToString(",") { it.chunk.id.toString() }}"
        }
