enum class RoleChange {
    CANDIDATE,
    LEADER,
    FOLLOWER,
}

class Event(val time: Long, val node: String, val change: RoleChange, val term: Int)
