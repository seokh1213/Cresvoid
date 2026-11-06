enum class Phase { COLD, WARMUP, DISCARD, MEASURE }
enum class Retention { USED, DISCARDED }

data class Sample(
    val phase: Phase,
    val candidate: Candidate,
    val calls: Int,
    val retention: Retention,
    val checksum: Long,
    val elapsedNanos: Long,
) {
    fun line(): String =
        "${phase.name.lowercase()}|$candidate|$calls|$retention|$checksum|$elapsedNanos"
}

data class Batch(val phase: Phase, val candidate: Candidate, val calls: Int, val retention: Retention)

class MeasurementRig(private val seed: Int) {
    @Volatile private var sink = 0L

    fun sample(batch: Batch): Sample {
        require(batch.calls in 1..1_000_000)
        val started = System.nanoTime()
        val checksum = when (batch.retention) {
            Retention.USED -> used(batch.candidate, batch.calls)
            Retention.DISCARDED -> {
                discarded(batch.candidate, batch.calls)
                0L
            }
        }
        val elapsed = System.nanoTime() - started
        sink = checksum
        return Sample(batch.phase, batch.candidate, batch.calls, batch.retention, sink, elapsed)
    }

    private fun used(candidate: Candidate, calls: Int): Long {
        var total = 0L
        when (candidate) {
            Candidate.A -> for (index in 0 until calls) total += Kernels.a(seed + index).toLong()
            Candidate.B -> for (index in 0 until calls) total += Kernels.b(seed + index).toLong()
        }
        return total
    }

    private fun discarded(candidate: Candidate, calls: Int) {
        // These loops intentionally expose dead-code elimination; keep their results unobserved.
        when (candidate) {
            Candidate.A -> for (index in 0 until calls) Kernels.a(seed + index)
            Candidate.B -> for (index in 0 until calls) Kernels.b(seed + index)
        }
    }
}
