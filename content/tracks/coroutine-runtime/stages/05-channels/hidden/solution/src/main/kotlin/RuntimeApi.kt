import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

enum class StartMode { EAGER, LAZY }
enum class JobState { NEW, ACTIVE, COMPLETING, CANCELLING, COMPLETED, CANCELLED, FAILED }

interface RuntimeJob {
    val state: JobState
    fun start()
    fun cancel()
    suspend fun join()
    fun invokeOnCompletion(handler: (JobState) -> Unit)
}

interface RuntimeDeferred<T> : RuntimeJob { suspend fun await(): T }

interface RuntimeScope {
    val job: RuntimeJob
    val context: CoroutineContext
    suspend fun delay(millis: Long)
    suspend fun yieldNow()
    fun ensureActive()
    fun launch(start: StartMode = StartMode.EAGER, context: CoroutineContext = EmptyCoroutineContext, block: suspend RuntimeScope.() -> Unit): RuntimeJob
    fun <T> async(start: StartMode = StartMode.EAGER, context: CoroutineContext = EmptyCoroutineContext, block: suspend RuntimeScope.() -> T): RuntimeDeferred<T>
    suspend fun scoped(block: suspend RuntimeScope.() -> Unit) { launch(block = block).join() }
    suspend fun supervised(block: suspend RuntimeScope.() -> Unit) { launch(context = SupervisorElement, block = block).join() }
    suspend fun withTimeout(millis: Long, block: suspend RuntimeScope.() -> Unit): Boolean = error("Use the command-level timeout")
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

data class TagElement(val value: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TagElement>
    override val key: CoroutineContext.Key<*> = Key
}

data class TraceElement(val value: String) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<TraceElement>
    override val key: CoroutineContext.Key<*> = Key
}

class JobElement(val owner: TaskJob<*>) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<JobElement>
    override val key: CoroutineContext.Key<*> = Key
}

object SupervisorElement : CoroutineContext.Element {
    object Key : CoroutineContext.Key<SupervisorElement>
    override val key: CoroutineContext.Key<*> = Key
}

class JobFailure(val code: String) : RuntimeException(code)
