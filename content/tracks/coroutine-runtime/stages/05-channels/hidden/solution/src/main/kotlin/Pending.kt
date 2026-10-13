import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation

class Pending<T>(
    private val runtime: VirtualRuntime,
    private val owner: TaskJob<*>,
    private val continuation: Continuation<T>,
    private val cancellable: Boolean = true,
) {
    private var active = true
    var onFinish: (() -> Unit)? = null

    fun deliver(value: T) {
        deliverResult(Result.success(value))
    }

    fun deliverResult(result: Result<T>) {
        if (!active) return
        active = false
        owner.clearPending(this)
        onFinish?.invoke()
        runtime.inJob(owner) {
            if (cancellable && owner.isCancelling()) continuation.resumeWith(Result.failure(CancellationException()))
            else continuation.resumeWith(result)
        }
    }

    fun cancel() {
        if (!active || !cancellable) return
        active = false
        owner.clearPending(this)
        onFinish?.invoke()
        runtime.enqueue { runtime.inJob(owner) { continuation.resumeWith(Result.failure(CancellationException())) } }
    }
}
