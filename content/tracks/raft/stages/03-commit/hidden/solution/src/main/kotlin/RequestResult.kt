sealed interface RequestResult {
    class Accepted(val index: Int, val term: Int) : RequestResult

    class NotLeader(val leader: String?) : RequestResult
}
