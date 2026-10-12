private const val MAX_ENTRIES_PER_APPEND = 4

class RaftNode(
    val name: String,
    private val electionTimeouts: List<Long>,
    private val clusterNodes: Collection<String>,
    private val network: Network,
    private val clock: Clock,
    private val publish: (Event) -> Unit,
) {
    var term: Int = 0
        private set
    var votedFor: String? = null
        private set
    var leader: String? = null
        private set
    var role: Role = Role.Follower
        private set
    var electionDeadline: Long = electionTimeouts.first()
        private set
    val log = RaftLog()
    private var electionsStarted = 0

    fun runTimer() {
        when (val current = role) {
            is Role.Leader -> if (current.electedAt < clock.now) sendHeartbeats(current)
            is Role.Candidate, Role.Follower -> if (electionDeadline == clock.now) startElection()
        }
    }

    fun receive(sender: String, message: Message) {
        if (message.term > term) adoptTerm(message.term)
        when (message) {
            is Message.RequestVote -> answerRequestVote(sender, message)
            is Message.RequestVoteResponse -> countVote(sender, message)
            is Message.AppendEntries -> answerAppendEntries(sender, message)
            is Message.AppendEntriesResponse -> trackReplication(sender, message)
        }
    }

    fun acceptClientCommand(key: String, value: String): RequestResult {
        if (role !is Role.Leader) return RequestResult.NotLeader(leader)
        val index = log.append(LogEntry(term, key, value))
        return RequestResult.Accepted(index, term)
    }

    private fun adoptTerm(newerTerm: Int) {
        term = newerTerm
        votedFor = null
        leader = null
        if (role !is Role.Follower) becomeFollower()
    }

    private fun startElection() {
        term++
        role = Role.Candidate(voters = mutableSetOf(name))
        votedFor = name
        leader = null
        electionsStarted++
        restartElectionTimer()
        report(RoleChange.CANDIDATE)
        if (isMajority(1)) becomeLeader() else sendToPeers(Message.RequestVote(term))
    }

    private fun answerRequestVote(candidate: String, request: Message.RequestVote) {
        val voteGranted = request.term == term && votedFor == null
        if (voteGranted) {
            votedFor = candidate
            restartElectionTimer()
        }
        network.send(name, candidate, Message.RequestVoteResponse(term, voteGranted))
    }

    private fun countVote(voter: String, response: Message.RequestVoteResponse) {
        val candidacy = role as? Role.Candidate ?: return
        if (response.term != term || !response.voteGranted) return
        candidacy.voters += voter
        if (isMajority(candidacy.voters.size)) becomeLeader()
    }

    private fun becomeLeader() {
        val leadership = Role.Leader(
            electedAt = clock.now,
            followers = peers().associateWith { FollowerProgress(nextIndex = log.lastIndex + 1) },
        )
        role = leadership
        leader = name
        report(RoleChange.LEADER)
        sendHeartbeats(leadership)
    }

    private fun sendHeartbeats(leadership: Role.Leader) {
        for ((follower, progress) in leadership.followers) {
            val prevLogIndex = progress.nextIndex - 1
            val request = Message.AppendEntries(
                term,
                prevLogIndex,
                prevLogTerm = log.termAt(prevLogIndex),
                entries = log.entriesFrom(progress.nextIndex, MAX_ENTRIES_PER_APPEND),
            )
            network.send(name, follower, request)
        }
    }

    private fun answerAppendEntries(sender: String, request: Message.AppendEntries) {
        if (request.term < term) {
            replyToAppend(sender, success = false, responseIndex = request.prevLogIndex)
            return
        }
        if (role is Role.Candidate) becomeFollower()
        leader = sender
        restartElectionTimer()
        if (!log.hasEntry(request.prevLogIndex, request.prevLogTerm)) {
            replyToAppend(sender, success = false, responseIndex = request.prevLogIndex)
            return
        }
        log.storeEntriesAfter(request.prevLogIndex, request.entries)
        replyToAppend(sender, success = true, responseIndex = request.prevLogIndex + request.entries.size)
    }

    private fun trackReplication(follower: String, response: Message.AppendEntriesResponse) {
        val leadership = role as? Role.Leader ?: return
        if (response.term != term) return
        val progress = leadership.followers.getValue(follower)
        if (response.success) {
            progress.recordSuccess(response.responseIndex)
        } else {
            progress.recordRejection(response.responseIndex)
        }
    }

    private fun replyToAppend(sender: String, success: Boolean, responseIndex: Int) {
        network.send(name, sender, Message.AppendEntriesResponse(term, success, responseIndex))
    }

    private fun becomeFollower() {
        role = Role.Follower
        report(RoleChange.FOLLOWER)
        restartElectionTimer()
    }

    private fun restartElectionTimer() {
        electionDeadline = clock.now + electionTimeouts[electionsStarted % electionTimeouts.size]
    }

    private fun isMajority(votes: Int): Boolean = votes * 2 > clusterNodes.size

    private fun peers(): List<String> = clusterNodes.filter { it != name }

    private fun sendToPeers(message: Message) {
        for (peer in peers()) network.send(name, peer, message)
    }

    private fun report(change: RoleChange) {
        publish(Event(clock.now, name, change, term))
    }
}
