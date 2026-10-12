private const val NONE = "-"

fun eventLine(event: Event): String = "${event.time} ${event.node} ${event.change} ${event.term}"

fun stateLine(node: RaftNode): String {
    val deadline = if (node.role is Role.Leader) NONE else node.electionDeadline.toString()
    return "${node.name} ${roleName(node.role)} term=${node.term} vote=${node.votedFor ?: NONE} " +
        "leader=${node.leader ?: NONE} deadline=$deadline"
}

private fun roleName(role: Role): String =
    when (role) {
        Role.Follower -> "follower"
        is Role.Candidate -> "candidate"
        is Role.Leader -> "leader"
    }
