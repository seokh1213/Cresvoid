import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

enum class StartMode {
    EAGER,
    LAZY,
}

enum class JobState {
    NEW,
    ACTIVE,
    COMPLETING,
    CANCELLING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

interface RuntimeJob {
    val state: JobState

    fun start() {
        TODO()
    }

    fun cancel() {
        TODO()
    }

    suspend fun join() {
        TODO()
    }

    fun invokeOnCompletion(handler: (JobState) -> Unit) {
        TODO()
    }
}

interface RuntimeDeferred<T> : RuntimeJob {
    suspend fun await(): T
}

interface RuntimeScope {
    val job: RuntimeJob
        get() = TODO()

    val context: CoroutineContext
        get() = TODO()

    suspend fun delay(millis: Long)

    suspend fun yieldNow() {
        TODO()
    }

    fun ensureActive() {
        TODO()
    }

    fun launch(
        start: StartMode = StartMode.EAGER,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend RuntimeScope.() -> Unit,
    ): RuntimeJob

    fun <T> async(
        start: StartMode = StartMode.EAGER,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend RuntimeScope.() -> T,
    ): RuntimeDeferred<T>

    suspend fun scoped(block: suspend RuntimeScope.() -> Unit) {
        TODO()
    }

    suspend fun supervised(block: suspend RuntimeScope.() -> Unit) {
        TODO()
    }

    suspend fun withTimeout(millis: Long, block: suspend RuntimeScope.() -> Unit): Boolean = TODO()

    fun <T> channel(capacity: Int): RuntimeChannel<T> = TODO()

    fun mutex(): RuntimeMutex = TODO()

    fun <T> flow(block: suspend FlowEmitter<T>.() -> Unit): RuntimeFlow<T> = TODO()
}

interface RuntimeChannel<T> {
    suspend fun send(value: T)

    suspend fun receive(): T

    fun close()
}

interface RuntimeMutex {
    suspend fun lock()

    fun unlock()
}

interface FlowEmitter<T> {
    suspend fun emit(value: T)
}

interface RuntimeFlow<T> {
    suspend fun collect(collector: suspend (T) -> Unit)

    fun <R> map(transform: suspend (T) -> R): RuntimeFlow<R>

    fun filter(predicate: suspend (T) -> Boolean): RuntimeFlow<T>

    fun take(count: Int): RuntimeFlow<T>
}

class VirtualRuntime {
    val now: Long
        get() = TODO()

    fun run(block: suspend RuntimeScope.() -> Unit) {
        TODO()
    }

    fun dispatch(continuation: Continuation<Unit>) {
        TODO()
    }
}
