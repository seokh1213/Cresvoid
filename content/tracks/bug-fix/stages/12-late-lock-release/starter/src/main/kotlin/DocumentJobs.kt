internal enum class JobState { RUNNING, QUEUED, DONE }

internal data class DocumentJob(
    val id: String,
    val resource: String,
    val lease: Lease,
    var state: JobState = JobState.RUNNING,
)

internal sealed interface StartResult {
    data class Started(val lease: Lease) : StartResult
    data class Busy(val lease: Lease) : StartResult
    data class Exists(val state: JobState) : StartResult
}

internal class DocumentJobs(private val repository: LeaseRepository) {
    private val jobs = mutableMapOf<String, DocumentJob>()
    private val completions = ArrayDeque<String>()

    fun start(command: LeaseCommand.Start, now: Long): StartResult {
        val existing = jobs[command.job]
        if (existing != null) return StartResult.Exists(existing.state)
        return when (val acquisition = repository.acquire(command.request, now)) {
            is Acquisition.Busy -> StartResult.Busy(acquisition.lease)
            is Acquisition.Granted -> {
                jobs[command.job] = DocumentJob(
                    id = command.job,
                    resource = command.request.resource,
                    lease = acquisition.lease,
                )
                StartResult.Started(acquisition.lease)
            }
        }
    }

    fun complete(id: String): JobState? {
        val job = jobs[id] ?: return null
        if (job.state == JobState.RUNNING) {
            completions.addLast(id)
            job.state = JobState.QUEUED
        }
        return job.state
    }

    fun flush(limit: Int, now: Long): Int {
        var delivered = 0
        while (delivered < limit && completions.isNotEmpty()) {
            val job = jobs.getValue(completions.removeFirst())
            val request = completionRequest(job)
            repository.release(request, now)
            job.state = JobState.DONE
            delivered++
        }
        return delivered
    }

    fun state(id: String): JobState? = jobs[id]?.state

    private fun completionRequest(job: DocumentJob): ReleaseRequest {
        val lease = job.lease
        return ReleaseRequest(job.resource, lease.client, lease.number)
    }
}
