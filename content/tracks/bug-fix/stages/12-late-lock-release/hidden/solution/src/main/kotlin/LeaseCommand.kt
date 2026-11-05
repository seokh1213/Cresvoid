internal sealed interface LeaseCommand {
    data class Start(val job: String, val request: AcquireRequest) : LeaseCommand
    data class Complete(val job: String) : LeaseCommand
    data class Flush(val limit: Int) : LeaseCommand
    data class Tick(val elapsed: Long) : LeaseCommand
    data class Status(val resource: String) : LeaseCommand
    data class Job(val job: String) : LeaseCommand
}

internal object LeaseCommandParser {
    private val separator = Regex("\\s+")

    fun parse(line: String): LeaseCommand {
        val fields = line.trim().split(separator)
        return when (fields[0]) {
            "START" -> {
                require(fields.size == 5)
                LeaseCommand.Start(
                    job = fields[1],
                    request = AcquireRequest(fields[2], fields[3], fields[4].toLong()),
                )
            }
            "COMPLETE" -> {
                require(fields.size == 2)
                LeaseCommand.Complete(fields[1])
            }
            "FLUSH" -> {
                require(fields.size == 2)
                LeaseCommand.Flush(fields[1].toInt())
            }
            "TICK" -> {
                require(fields.size == 2)
                LeaseCommand.Tick(fields[1].toLong())
            }
            "STATUS" -> {
                require(fields.size == 2)
                LeaseCommand.Status(fields[1])
            }
            "JOB" -> {
                require(fields.size == 2)
                LeaseCommand.Job(fields[1])
            }
            else -> error("Unknown command")
        }
    }
}
