enum class OptimizerStatus {
    OK,
    FAIL,
}

enum class CandidateSource {
    ORGANIC,
    RECOMMENDED,
}

data class Candidate(val productId: String, val score: Int)

data class PlacementRequest(
    val status: OptimizerStatus,
    val slots: Int,
    val recommendationLimit: Int,
    val organic: List<Candidate>,
    val recommended: List<Candidate>,
)

data class Placement(val candidates: List<Candidate>, val score: Int) {
    fun render(route: String): String {
        val productIds = candidates.joinToString(",") { it.productId }
        return "$route $productIds $score"
    }
}

class PlacementPrefix(private val request: PlacementRequest) {
    private val selected = mutableListOf<Candidate>()
    private val sources = mutableListOf<CandidateSource>()
    var organicUsed: Int = 0
        private set
    var recommendedUsed: Int = 0
        private set
    var score: Int = 0
        private set

    val size: Int get() = selected.size

    fun append(source: CandidateSource, candidate: Candidate) {
        score += candidate.score * (request.slots - size)
        selected.add(candidate)
        sources.add(source)
        when (source) {
            CandidateSource.ORGANIC -> organicUsed++
            CandidateSource.RECOMMENDED -> recommendedUsed++
        }
    }

    fun removeLast() {
        val candidate = selected.removeLast()
        score -= candidate.score * (request.slots - size)
        when (sources.removeLast()) {
            CandidateSource.ORGANIC -> organicUsed--
            CandidateSource.RECOMMENDED -> recommendedUsed--
        }
    }

    fun snapshot(): Placement = Placement(selected.toList(), score)
}
