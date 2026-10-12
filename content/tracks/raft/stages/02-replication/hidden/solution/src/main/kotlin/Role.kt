sealed interface Role {
    data object Follower : Role

    class Candidate(val voters: MutableSet<String>) : Role

    class Leader(val electedAt: Long, val followers: Map<String, FollowerProgress>) : Role
}
