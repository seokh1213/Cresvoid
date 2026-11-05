data class Candidate(val id: String, val score: Int) {
    init {
        require(id.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")))
        require(score in 0..1_000_000)
    }
}

class CandidatePool(candidates: List<Candidate>) {
    private val snapshot = candidates.toList()

    init {
        require(snapshot.map { it.id }.toSet().size == snapshot.size)
    }

    fun eligible(qualityPercent: Int): List<Candidate> {
        require(qualityPercent in 0..100)
        val ranked = ranked()
        val bestScore = ranked.firstOrNull()?.score ?: return emptyList()
        val minimumScore = minimumScore(bestScore, qualityPercent)
        return ranked.filter { it.score >= minimumScore }
    }

    private fun ranked(): List<Candidate> {
        val byId = snapshot.sortedBy { it.id }
        return byId.sortedByDescending { it.score }
    }

    private fun minimumScore(bestScore: Int, qualityPercent: Int): Int {
        return ((bestScore.toLong() * qualityPercent + 99) / 100).toInt()
    }
}

class PoolCatalog {
    private val pools = mutableMapOf<String, CandidatePool>()

    fun replace(name: String, candidates: List<Candidate>) {
        val replacement = CandidatePool(candidates)
        pools[name] = replacement
    }

    fun get(name: String): CandidatePool {
        return requireNotNull(pools[name]) { "unknown pool" }
    }
}
