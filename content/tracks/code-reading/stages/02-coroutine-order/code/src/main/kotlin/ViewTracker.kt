import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

const val RECENT_VIEW_TIMEOUT_MILLIS = 150L

class ViewTracker(
    private val gateway: DownstreamGateway,
    dispatcher: CoroutineDispatcher,
    report: (String) -> Unit,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { context, failure ->
            report("tracking failed ${context[CoroutineName]?.name}: ${failure.message}")
        },
    )

    fun record(productId: String) {
        val product = CoroutineName(productId)
        scope.launch(product) { gateway.call("counter") {} }
        scope.launch(product) { withTimeout(RECENT_VIEW_TIMEOUT_MILLIS) { gateway.call("recent") {} } }
    }

    suspend fun awaitPending() {
        scope.coroutineContext.job.children.toList().joinAll()
    }
}
