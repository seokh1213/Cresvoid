sealed interface Command {
    data class DefineTokenBucket(val name: String, val capacity: Long, val refillPerSecond: Long) : Command

    data class DefineLeakyBucket(val name: String, val queueLength: Long, val leakInterval: Long) : Command

    data class DefineWindow(val name: String, val limit: Long, val windowLength: Long) : Command

    data class DefineReadWrite(val name: String, val limit: Long, val windowLength: Long) : Command

    data class DefineAtomic(val name: String, val limit: Long, val windowLength: Long) : Command

    data class DefineLocalCopy(val name: String, val limit: Long, val windowLength: Long, val syncPeriod: Long) : Command

    data class RegisterGateway(val name: String, val delay: Long, val workers: Long, val processingTime: Long) : Command

    data class Request(val limiter: String, val key: String, val time: Long) : Command

    data class Report(val limiter: String) : Command

    data class Call(val limiter: String, val key: String, val time: Long, val gateway: String, val priority: Priority) : Command

    data class Peek(val limiter: String, val key: String, val time: Long) : Command

    data class StopStore(val time: Long) : Command

    data class StartStore(val time: Long) : Command
}
