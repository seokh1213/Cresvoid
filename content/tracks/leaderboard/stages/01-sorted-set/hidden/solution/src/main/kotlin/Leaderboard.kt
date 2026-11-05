class Leaderboard {
    private val participants = HashMap<Int, Participant>()
    private val ranking = RankedIndex()
    private var arrival = 0L

    fun update(user: Int, value: Long, kind: UpdateKind): Boolean {
        val previous = participants[user]
        val score = when (kind) {
            UpdateKind.ADD -> (previous?.score ?: 0L) + value
            UpdateKind.SET -> value
        }
        if (score !in -SCORE_LIMIT..SCORE_LIMIT) return false
        if (previous != null && previous.score == score) return true
        val current = Participant(user, score, ++arrival)
        if (previous != null) ranking.remove(previous)
        participants[user] = current
        ranking.insert(current)
        return true
    }

    fun rank(user: Int): String {
        val participant = participants[user] ?: return "(none)"
        return "${ranking.countBefore(participant) + 1} ${participant.score}"
    }
}
