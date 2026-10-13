import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

class TaskJob<T>(
    private val runtime: VirtualRuntime,
    private val parent: TaskJob<*>?,
    inherited: CoroutineContext,
    private val block: suspend RuntimeScope.() -> T,
    private val async: Boolean = false,
    private val supervisesChildren: Boolean = false,
) : RuntimeScope, RuntimeDeferred<T> {
    override val context: CoroutineContext = inherited + JobElement(this)
    private val children = linkedSetOf<TaskJob<*>>()
    private val waiters = mutableListOf<() -> Unit>()
    private val handlers = mutableListOf<(JobState) -> Unit>()
    private var pending: Pending<*>? = null
    private var result: T? = null
    var failureCode: String? = null
        private set
    private var bodyFinished = false
    override var state: JobState = JobState.NEW
        private set
    override val job: RuntimeJob get() = this

    override fun start() {
        if (state != JobState.NEW) return
        state = JobState.ACTIVE
        runtime.enqueue {
            runtime.record(context, "START")
            block.startCoroutine(this, object : Continuation<T> {
                override val context: CoroutineContext = this@TaskJob.context
                override fun resumeWith(result: Result<T>) {
                    when (val failure = result.exceptionOrNull()) {
                        is JobFailure -> fail(failure.code)
                        is CancellationException -> cancel()
                        null -> Unit
                        else -> throw failure
                    }
                    this@TaskJob.result = result.getOrNull()
                    bodyFinished = true
                    finishIfReady()
                }
            })
        }
    }

    private fun finishIfReady() {
        if (!bodyFinished || state in setOf(JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED)) return
        if (children.isNotEmpty()) {
            if (!isCancelling()) state = JobState.COMPLETING
            return
        }
        state = when {
            failureCode != null -> JobState.FAILED
            isCancelling() -> JobState.CANCELLED
            else -> JobState.COMPLETED
        }
        finish()
    }

    private fun finish() {
        val event = when (state) {
            JobState.COMPLETED -> "DONE"
            JobState.FAILED -> "FAILED $failureCode"
            else -> "CANCELLED"
        }
        runtime.record(context, event)
        handlers.forEach { it(state) }
        if (state == JobState.FAILED && (parent?.parent == null || parent.supervisesChildren && !async)) {
            runtime.record(context, "UNHANDLED $failureCode")
        }
        parent?.childFinished(this)
        waiters.forEach(runtime::enqueue)
        waiters.clear()
    }

    private fun childFinished(child: TaskJob<*>) {
        children.remove(child)
        finishIfReady()
    }

    override fun cancel() {
        if (state in setOf(JobState.CANCELLING, JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED)) return
        if (state == JobState.NEW) {
            state = JobState.CANCELLED
            finish()
            return
        }
        state = JobState.CANCELLING
        pending?.cancel()
        children.toList().forEach(TaskJob<*>::cancel)
        finishIfReady()
    }

    fun fail(code: String) {
        if (state in setOf(JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED)) return
        if (failureCode != null) {
            runtime.record(context, "SUPPRESSED $code")
            return
        }
        failureCode = code
        state = JobState.CANCELLING
        pending?.cancel()
        children.toList().forEach { it.cancel() }
        if (parent != null && parent.parent != null && !parent.supervisesChildren) parent.fail(code)
        finishIfReady()
    }

    override fun invokeOnCompletion(handler: (JobState) -> Unit) {
        if (state in setOf(JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED)) handler(state)
        else handlers.add(handler)
    }

    override suspend fun await(): T {
        val caller = currentJob()
        caller.ensureActive()
        start()
        if (state == JobState.CANCELLED) throw CancellationException()
        if (state == JobState.FAILED) throw JobFailure(failureCode!!)
        if (state == JobState.COMPLETED) return completedResult()
        return suspendCoroutine { continuation ->
            val suspension = Pending(runtime, caller, continuation)
            caller.setPending(suspension)
            waiters.add {
                if (state == JobState.CANCELLED) suspension.deliverResult(Result.failure(CancellationException()))
                else if (state == JobState.FAILED) suspension.deliverResult(Result.failure(JobFailure(failureCode!!)))
                else suspension.deliver(completedResult())
            }
        }
    }

    override suspend fun join() {
        val caller = currentJob()
        caller.ensureActive()
        start()
        if (state in setOf(JobState.COMPLETED, JobState.CANCELLED, JobState.FAILED)) return
        suspendCoroutine<Unit> { continuation ->
            val suspension = Pending(runtime, caller, continuation)
            caller.setPending(suspension)
            waiters.add { suspension.deliver(Unit) }
        }
    }

    override fun ensureActive() {
        if (isCancelling()) throw CancellationException()
    }

    fun isCancelling(): Boolean = state in setOf(JobState.CANCELLING, JobState.CANCELLED, JobState.FAILED)

    override suspend fun delay(millis: Long) {
        ensureActive()
        suspendCoroutine<Unit> { continuation ->
            val suspension = Pending(runtime, this, continuation)
            setPending(suspension)
            val timer = runtime.schedule(millis) { suspension.deliver(Unit) }
            suspension.onFinish = timer::cancel
        }
    }

    override suspend fun yieldNow() {
        ensureActive()
        suspendCoroutine<Unit> { continuation ->
            val suspension = Pending(runtime, this, continuation)
            setPending(suspension)
            runtime.enqueue { suspension.deliver(Unit) }
        }
    }

    suspend fun shieldDelay(millis: Long) {
        suspendCoroutine<Unit> { continuation ->
            val suspension = Pending(runtime, this, continuation, cancellable = false)
            setPending(suspension)
            val timer = runtime.schedule(millis) { suspension.deliver(Unit) }
            suspension.onFinish = timer::cancel
        }
    }

    override suspend fun withTimeout(millis: Long, block: suspend RuntimeScope.() -> Unit): Boolean {
        ensureActive()
        var expired = false
        lateinit var child: RuntimeJob
        val timer = runtime.schedule(millis) { expired = true; child.cancel() }
        child = launch(block = block)
        try { child.join() } finally { timer.cancel() }
        return !expired
    }

    override fun launch(start: StartMode, context: CoroutineContext, block: suspend RuntimeScope.() -> Unit): RuntimeJob =
        createChild(start, context, block, async = false)

    override fun <R> async(start: StartMode, context: CoroutineContext, block: suspend RuntimeScope.() -> R): RuntimeDeferred<R> =
        createChild(start, context, block, async = true)

    private fun <R> createChild(start: StartMode, addition: CoroutineContext, block: suspend RuntimeScope.() -> R, async: Boolean): TaskJob<R> {
        val child = TaskJob(runtime, this, context + addition, block, async, addition[SupervisorElement.Key] != null)
        children.add(child)
        if (start == StartMode.EAGER) child.start()
        if (isCancelling()) child.cancel()
        return child
    }

    internal fun setPending(value: Pending<*>) { pending = value }
    internal fun clearPending(value: Pending<*>) { if (pending === value) pending = null }

    @Suppress("UNCHECKED_CAST")
    private fun completedResult(): T = result as T
}

private suspend fun currentJob(): TaskJob<*> =
    coroutineContext[JobElement]?.owner ?: error("Missing active Job")

suspend fun RuntimeScope.shieldDelay(millis: Long) {
    (job as TaskJob<*>).shieldDelay(millis)
}
