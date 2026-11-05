class PlacementService {
    fun place(request: PlacementRequest): String = when (request.status) {
        OptimizerStatus.OK -> {
            val placement = OptimalPlacementSearch(request).solve()
            placement?.render("OPTIMAL") ?: "IMPOSSIBLE"
        }
        OptimizerStatus.FAIL -> {
            val placement = GreedyPlacement(request).solve()
            placement?.render("FALLBACK") ?: "IMPOSSIBLE"
        }
    }
}

private class OptimalPlacementSearch(private val request: PlacementRequest) {
    private val prefix = PlacementPrefix(request)
    private var best: Placement? = null

    fun solve(): Placement? {
        visit()
        return best
    }

    private fun visit() {
        if (prefix.size == request.slots) {
            val previous = best
            if (previous == null || prefix.score > previous.score) {
                best = prefix.snapshot()
            }
            return
        }
        val organic = request.organic.getOrNull(prefix.organicUsed)
        if (organic != null) {
            explore(CandidateSource.ORGANIC, organic)
        }
        val recommended = request.recommended.getOrNull(prefix.recommendedUsed)
        if (recommended != null && prefix.recommendedUsed < request.recommendationLimit) {
            explore(CandidateSource.RECOMMENDED, recommended)
        }
    }

    private fun explore(source: CandidateSource, candidate: Candidate) {
        prefix.append(source, candidate)
        visit()
        prefix.removeLast()
    }
}

private class GreedyPlacement(private val request: PlacementRequest) {
    private val prefix = PlacementPrefix(request)

    fun solve(): Placement? {
        while (prefix.size < request.slots) {
            val organic = request.organic.getOrNull(prefix.organicUsed)
            val recommended = nextRecommended()
            val source = chooseSource(organic, recommended) ?: return null
            val candidate = when (source) {
                CandidateSource.ORGANIC -> organic
                CandidateSource.RECOMMENDED -> recommended
            } ?: return null
            prefix.append(source, candidate)
        }
        return prefix.snapshot()
    }

    private fun nextRecommended(): Candidate? {
        return request.recommended.getOrNull(prefix.recommendedUsed)
    }

    private fun chooseSource(organic: Candidate?, recommended: Candidate?): CandidateSource? = when {
        organic == null && recommended == null -> null
        organic == null -> CandidateSource.RECOMMENDED
        recommended == null -> CandidateSource.ORGANIC
        organic.score >= recommended.score -> CandidateSource.ORGANIC
        else -> CandidateSource.RECOMMENDED
    }
}
