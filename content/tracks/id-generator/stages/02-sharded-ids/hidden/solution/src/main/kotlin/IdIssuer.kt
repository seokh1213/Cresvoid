sealed interface IssueResult {
    data class Issued(val id: Long) : IssueResult
    data class Failed(val reason: String) : IssueResult

    fun render(): String = when (this) {
        is Issued -> "ID $id"
        is Failed -> "ERR $reason"
    }
}

private data class LastIssue(val time: Long, val sequence: Long)

class IdIssuer(private val machine: Long) {
    private var last: LastIssue? = null

    fun next(settings: Settings, samples: List<Long>): IssueResult {
        val layout = settings.layout
        if (samples.any { !layout.containsTime(it) }) return IssueResult.Failed("TIME")
        val previous = last
        for (time in samples) {
            if (previous != null && time < previous.time) {
                if (settings.policy == ClockPolicy.REJECT) return IssueResult.Failed("CLOCK")
                continue
            }
            val sequence = if (previous == null || time > previous.time) 0 else previous.sequence + 1
            if (sequence > layout.maxSequence) continue
            val id = layout.encode(time, machine, sequence)
            last = LastIssue(time, sequence)
            return IssueResult.Issued(id)
        }
        return IssueResult.Failed("WAIT")
    }
}
