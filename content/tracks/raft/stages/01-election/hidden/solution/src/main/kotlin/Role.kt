sealed interface Role {
    data object Follower : Role

    class Candidate(val voters: MutableSet<String>) : Role

    class Leader(val electedAt: Long) : Role
}
