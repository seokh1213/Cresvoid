import java.util.PriorityQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

enum class StartMode { EAGER, LAZY }

enum class JobState { NEW, ACTIVE, COMPLETING, CANCELLING, COMPLETED, CANCELLED, FAILED }

interface RuntimeJob {
    val state: JobState
    fun start()
    fun cancel()
    suspend fun join()
    fun invokeOnCompletion(handler: (JobState) -> Unit)
}

interface RuntimeDeferred<T> : RuntimeJob {
    suspend fun await(): T
}

interface RuntimeScope {
    val job: RuntimeJob
    val context: CoroutineContext
    suspend fun delay(millis: Long)
    suspend fun yieldNow(): Unit = error("Available in stage 3")
    fun ensureActive(): Unit = error("Available in stage 3")
    fun launch(start: StartMode = StartMode.EAGER, context: CoroutineContext = EmptyCoroutineContext, block: suspend RuntimeScope.() -> Unit): RuntimeJob
    fun <T> async(start: StartMode = StartMode.EAGER, context: CoroutineContext = EmptyCoroutineContext, block: suspend RuntimeScope.() -> T): RuntimeDeferred<T>
    suspend fun scoped(block: suspend RuntimeScope.() -> Unit): Unit = error("Available in stage 2")
    suspend fun supervised(block: suspend RuntimeScope.() -> Unit): Unit = error("Available in stage 4")
    suspend fun withTimeout(millis: Long, block: suspend RuntimeScope.() -> Unit): Boolean = error("Available in stage 3")
    fun <T> channel(capacity: Int): RuntimeChannel<T> = error("Available in stage 5")
    fun mutex(): RuntimeMutex = error("Available in stage 5")
    fun <T> flow(block: suspend FlowEmitter<T>.() -> Unit): RuntimeFlow<T> = error("Available in stage 6")
}

interface RuntimeChannel<T> { suspend fun send(value: T); suspend fun receive(): T; fun close() }
interface RuntimeMutex { suspend fun lock(); fun unlock() }
interface FlowEmitter<T> { suspend fun emit(value: T) }
interface RuntimeFlow<T> {
    suspend fun collect(collector: suspend (T) -> Unit)
    fun <R> map(transform: suspend (T) -> R): RuntimeFlow<R>
    fun filter(predicate: suspend (T) -> Boolean): RuntimeFlow<T>
    fun take(count: Int): RuntimeFlow<T>
}

data class PathElement(val path: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<PathElement>
    override val key: CoroutineContext.Key<*> = Key
}

private data class Timer(val at: Long, val order: Long, val resume: () -> Unit)

class VirtualRuntime(private val output: (Long, String, String) -> Unit = { _, _, _ -> }) {
    private val ready = ArrayDeque<() -> Unit>()
    private val timers = PriorityQueue(compareBy<Timer>({ it.at }, { it.order }))
    private var nextTimerOrder = 0L
    var now: Long = 0
        private set

    private val root = TaskJob<Unit>(this, null, EmptyCoroutineContext, {})

    fun run(block: suspend RuntimeScope.() -> Unit) {
        block.startCoroutine(root, object : Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
        })
        while (ready.isNotEmpty() || timers.isNotEmpty()) {
            if (ready.isEmpty()) {
                now = timers.peek().at
                do {
                    ready.addLast(timers.remove().resume)
                } while (timers.isNotEmpty() && timers.peek().at == now)
            }
            ready.removeFirst().invoke()
        }
    }

    fun launchRoot(path: String, block: suspend RuntimeScope.() -> Unit) {
        root.launch(context = PathElement(path), block = block)
    }

    fun dispatch(continuation: Continuation<Unit>) {
        enqueue { continuation.resume(Unit) }
    }

    internal fun enqueue(action: () -> Unit) { ready.addLast(action) }

    internal fun schedule(after: Long, continuation: Continuation<Unit>) {
        if (after == 0L) dispatch(continuation)
        else timers.add(Timer(now + after, nextTimerOrder++) { continuation.resume(Unit) })
    }

    internal fun record(context: CoroutineContext, event: String) {
        output(now, context[PathElement]?.path ?: "api", event)
    }
}

private class TaskJob<T>(
    private val runtime: VirtualRuntime,
    private val parent: TaskJob<*>?,
    override val context: CoroutineContext,
    private val block: suspend RuntimeScope.() -> T,
) : RuntimeScope, RuntimeDeferred<T> {
    private val children = mutableSetOf<TaskJob<*>>()
    private val waiters = mutableListOf<Continuation<T>>()
    private var result: T? = null
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
                    this@TaskJob.result = result.getOrThrow()
                    bodyFinished = true
                    finishIfReady()
                }
            })
        }
    }

    private fun finishIfReady() {
        if (!bodyFinished) return
        if (children.isNotEmpty()) {
            state = JobState.COMPLETING
            return
        }
        state = JobState.COMPLETED
        runtime.record(context, "DONE")
        parent?.childFinished(this)
        @Suppress("UNCHECKED_CAST")
        val completed = result as T
        waiters.forEach { waiter -> runtime.enqueue { waiter.resume(completed) } }
        waiters.clear()
    }

    private fun childFinished(child: TaskJob<*>) {
        children.remove(child)
        finishIfReady()
    }

    override suspend fun await(): T {
        if (state == JobState.COMPLETED) {
            @Suppress("UNCHECKED_CAST")
            return result as T
        }
        return suspendCoroutine { waiters.add(it) }
    }

    override suspend fun join() { error("Available in stage 2") }
    override fun cancel() { error("Available in stage 3") }
    override fun invokeOnCompletion(handler: (JobState) -> Unit) { error("Available in stage 3") }

    override suspend fun delay(millis: Long) {
        suspendCoroutine<Unit> { runtime.schedule(millis, it) }
    }

    override fun launch(start: StartMode, context: CoroutineContext, block: suspend RuntimeScope.() -> Unit): RuntimeJob =
        createChild(start, context, block)

    override fun <R> async(start: StartMode, context: CoroutineContext, block: suspend RuntimeScope.() -> R): RuntimeDeferred<R> =
        createChild(start, context, block)

    private fun <R> createChild(start: StartMode, addition: CoroutineContext, block: suspend RuntimeScope.() -> R): TaskJob<R> {
        val child = TaskJob(runtime, this, context + addition, block)
        children.add(child)
        if (start == StartMode.EAGER) child.start()
        return child
    }
}
