fun benchmark(rig: MeasurementRig, order: String): List<Sample> {
    val batches = buildList {
        for (candidate in Candidate.entries) add(Batch(Phase.COLD, candidate, 1_000, Retention.USED))
        for (candidate in Candidate.entries) add(Batch(Phase.WARMUP, candidate, 60_000, Retention.USED))
        for (candidate in Candidate.entries) add(Batch(Phase.DISCARD, candidate, 500_000, Retention.DISCARDED))
        val candidates = if (order == "abba") listOf(Candidate.A, Candidate.A, Candidate.B, Candidate.B)
            else listOf(Candidate.B, Candidate.B, Candidate.A, Candidate.A)
        for (candidate in candidates) add(Batch(Phase.MEASURE, candidate, 500_000, Retention.DISCARDED))
    }
    return batches.map { rig.sample(it) }
}
