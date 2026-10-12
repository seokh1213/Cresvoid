class SeriesRegistry<S : Series>(private val createSeries: (Set<LabelPair>) -> S) {
    private val seriesByLabels = HashMap<Set<LabelPair>, S>()
    private val seriesByLabelPair = HashMap<LabelPair, MutableList<S>>()
    private val allSeries = ArrayList<S>()

    fun seriesForNewSample(labels: Set<LabelPair>, time: Long): S {
        val existing = seriesByLabels[labels] ?: return register(labels)
        if (time <= existing.lastTime) reject(ErrorCode.OUT_OF_ORDER)
        return existing
    }

    fun select(required: Set<LabelPair>): List<S> {
        if (required.isEmpty()) return allSeries
        val narrowest = required.map { pair -> seriesByLabelPair[pair].orEmpty() }.minBy { it.size }
        return narrowest.filter { series -> series.labels.containsAll(required) }
    }

    private fun register(labels: Set<LabelPair>): S {
        val series = createSeries(labels)
        seriesByLabels[labels] = series
        allSeries += series
        for (pair in labels) seriesByLabelPair.getOrPut(pair, ::ArrayList) += series
        return series
    }
}
