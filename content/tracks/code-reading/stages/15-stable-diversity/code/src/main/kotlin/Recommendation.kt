import java.util.Random

class RecommendationSelector {
    fun select(pool: CandidatePool, request: RecommendationRequest): List<String> {
        val eligible = pool.eligible(request.qualityPercent)
        val random = Random(RequestSeed.forRequest(request))
        return draw(eligible, request.count, random).map { it.id }
    }

    private fun draw(
        eligible: List<Candidate>,
        count: Int,
        random: Random,
    ): List<Candidate> {
        val remaining = eligible.toMutableList()
        val selected = mutableListOf<Candidate>()
        while (selected.size < count && remaining.isNotEmpty()) {
            val index = random.nextInt(remaining.size)
            selected += remaining.removeAt(index)
        }
        return selected
    }
}

class RecommendationArchive {
    private val results = mutableMapOf<String, List<String>>()

    fun save(name: String, ids: List<String>) {
        results[name] = ids.toList()
    }

    fun render(names: List<String>): String {
        val saved = names.map { name ->
            requireNotNull(results[name]) { "unknown result" }
        }
        return saved.joinToString(" | ") { ids ->
            if (ids.isEmpty()) "(none)" else ids.joinToString(",")
        }
    }
}
