import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin

data class EventPlan(
    val id: String,
    val startedAt: Long,
    val duration: Long,
    val interval: Long,
) {
    val endsAt: Long = startedAt + duration
}

data class EventSnapshot(
    val id: String,
    val refreshes: Int,
    val startedAt: Long,
    val endsAt: Long,
) {
    fun render(): String = "EVENT $id $refreshes $startedAt $endsAt"
}

class CatalogEvent(
    private val plan: EventPlan,
    private val time: EventTime,
    private val onFinished: (String) -> Unit,
) {
    private var job: Job? = null
    private var refreshes = 0

    fun start(scope: CoroutineScope) {
        job = scope.launch { run() }
    }

    fun snapshot(): EventSnapshot =
        EventSnapshot(plan.id, refreshes, plan.startedAt, plan.endsAt)

    suspend fun stop() {
        job?.cancelAndJoin()
    }

    private suspend fun run() {
        try {
            while (job?.isActive == true && time.now() < plan.endsAt) {
                refreshes++
                val remaining = plan.endsAt - time.now()
                time.pause(minOf(plan.interval, remaining))
            }
        } finally {
            onFinished(plan.id)
        }
    }
}
