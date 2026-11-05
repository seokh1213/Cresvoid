fun main() {
    val repository = LeaseRepository()
    val console = LeaseConsole(repository, DocumentJobs(repository))
    System.`in`.bufferedReader().use { input ->
        System.out.bufferedWriter().use { output ->
            input.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                output.write(console.execute(LeaseCommandParser.parse(line)))
                output.newLine()
            }
        }
    }
}

internal class LeaseConsole(
    private val repository: LeaseRepository,
    private val jobs: DocumentJobs,
) {
    private var now = 0L

    fun execute(command: LeaseCommand): String = when (command) {
        is LeaseCommand.Start -> start(command)
        is LeaseCommand.Complete -> jobs.complete(command.job)?.name ?: "UNKNOWN"
        is LeaseCommand.Flush -> "FLUSHED ${jobs.flush(command.limit, now)}"
        is LeaseCommand.Tick -> tick(command.elapsed)
        is LeaseCommand.Status -> status(command.resource)
        is LeaseCommand.Job -> jobs.state(command.job)?.name ?: "UNKNOWN"
    }

    private fun start(command: LeaseCommand.Start): String =
        when (val result = jobs.start(command, now)) {
            is StartResult.Started -> "STARTED ${result.lease.number} ${result.lease.expiresAt}"
            is StartResult.Busy -> "BUSY ${describe(result.lease)}"
            is StartResult.Exists -> "EXISTS ${result.state}"
        }

    private fun tick(elapsed: Long): String {
        now += elapsed
        return "TIME $now"
    }

    private fun status(resource: String): String {
        val lease = repository.current(resource, now) ?: return "FREE"
        return "HELD ${describe(lease)}"
    }

    private fun describe(lease: Lease): String =
        "${lease.client} ${lease.number} ${lease.expiresAt}"
}
