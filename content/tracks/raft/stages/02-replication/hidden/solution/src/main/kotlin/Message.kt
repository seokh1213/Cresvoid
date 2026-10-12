sealed interface Message {
    val term: Int

    class RequestVote(override val term: Int) : Message

    class RequestVoteResponse(override val term: Int, val voteGranted: Boolean) : Message

    class AppendEntries(
        override val term: Int,
        val prevLogIndex: Int,
        val prevLogTerm: Int,
        val entries: List<LogEntry>,
    ) : Message

    class AppendEntriesResponse(override val term: Int, val success: Boolean, val responseIndex: Int) : Message
}

class Envelope(val sender: String, val receiver: String, val message: Message)
