import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

interface PullScope<T> {
    suspend fun yield(value: T)
}

fun <T> pullSequence(block: suspend PullScope<T>.() -> Unit): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> = PullIterator(block)
}

private class PullIterator<T>(
    private val block: suspend PullScope<T>.() -> Unit,
) : Iterator<T>, PullScope<T>, Continuation<Unit> {
    private enum class State { NEW, RUNNING, READY, SUSPENDED, DONE }

    override val context: CoroutineContext = EmptyCoroutineContext
    private var state = State.NEW
    private var pending: Continuation<Unit>? = null
    private var value: Any? = null
    private var failure: Throwable? = null

    override suspend fun yield(value: T) {
        suspendCoroutine<Unit> { continuation ->
            this.value = value
            pending = continuation
            state = State.READY
        }
    }

    override fun hasNext(): Boolean {
        when (state) {
            State.NEW -> {
                state = State.RUNNING
                block.startCoroutine(this, this)
            }
            State.SUSPENDED -> {
                state = State.RUNNING
                val continuation = checkNotNull(pending)
                pending = null
                continuation.resume(Unit)
            }
            State.READY -> return true
            State.DONE -> return false
            State.RUNNING -> error("The sequence cannot request itself")
        }
        failure?.let { cause ->
            failure = null
            throw cause
        }
        check(state != State.RUNNING) { "The sequence suspended outside yield" }
        return state == State.READY
    }

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        @Suppress("UNCHECKED_CAST")
        val next = value as T
        value = null
        state = State.SUSPENDED
        return next
    }

    override fun resumeWith(result: Result<Unit>) {
        state = State.DONE
        pending = null
        failure = result.exceptionOrNull()
    }
}
