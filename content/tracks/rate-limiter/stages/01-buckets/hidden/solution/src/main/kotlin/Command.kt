sealed interface Command {
    data class DefineTokenBucket(val name: String, val capacity: Long, val refillPerSecond: Long) : Command

    data class DefineLeakyBucket(val name: String, val queueLength: Long, val leakInterval: Long) : Command

    data class Request(val limiter: String, val key: String, val time: Long) : Command
}
