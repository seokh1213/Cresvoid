private const val NONE = "-"

fun eventLine(event: Event): String = "${event.time} ${event.node} ${event.change} ${event.term}"

fun stateLine(node: RaftNode): String {
    val deadline = if (node.role is Role.Leader) NONE else node.electionDeadline.toString()
    return "${node.name} ${roleName(node.role)} term=${node.term} vote=${node.votedFor ?: NONE} " +
        "leader=${node.leader ?: NONE} deadline=$deadline"
}

fun requestLine(result: RequestResult): String =
    when (result) {
        is RequestResult.Accepted -> "OK ${result.index} ${result.term}"
        is RequestResult.NotLeader -> notLeaderLine(result.leader)
    }

fun logLine(log: RaftLog): String =
    if (log.lastIndex == 0) "(empty)" else log.all.joinToString(" ") { "${it.term}:${it.key}=${it.value}" }

fun nextLines(node: RaftNode): List<String> {
    val leadership = node.role as? Role.Leader ?: return listOf(notLeaderLine(node.leader))
    if (leadership.followers.isEmpty()) return listOf("(none)")
    return leadership.followers.map { (follower, progress) ->
        "$follower next=${progress.nextIndex} match=${progress.matchIndex}"
    }
}

private fun notLeaderLine(leader: String?): String = "NOT_LEADER ${leader ?: NONE}"

private fun roleName(role: Role): String =
    when (role) {
        Role.Follower -> "follower"
        is Role.Candidate -> "candidate"
        is Role.Leader -> "leader"
    }
