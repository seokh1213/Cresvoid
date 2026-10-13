import java.util.PriorityQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine

class TimerHandle(val at: Long, val order: Long, val action: () -> Unit) {
    var active = true
        private set

    fun cancel() { active = false }
    fun fire() {
        if (!active) return
        active = false
        action()
    }
}

class VirtualRuntime(private val output: (Long, String, String) -> Unit = { _, _, _ -> }) {
    private val ready = ArrayDeque<() -> Unit>()
    private val timers = PriorityQueue(compareBy<TimerHandle>({ it.at }, { it.order }))
    private var nextTimerOrder = 0L
    var now: Long = 0
        private set
    var currentJob: TaskJob<*>? = null
        private set

    private val root = TaskJob<Unit>(this, null, EmptyCoroutineContext, {})

    fun run(block: suspend RuntimeScope.() -> Unit) {
        inJob(root) {
            block.startCoroutine(root, object : Continuation<Unit> {
                override val context: CoroutineContext = root.context
                override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
            })
        }
        while (true) {
            while (timers.peek()?.active == false) timers.remove()
            if (ready.isEmpty() && timers.isEmpty()) break
            if (ready.isEmpty()) moveNextTimers()
            ready.removeFirst().invoke()
        }
    }

    private fun moveNextTimers() {
        now = timers.peek().at
        do {
            val timer = timers.remove()
            if (timer.active) ready.addLast(timer::fire)
        } while (timers.isNotEmpty() && timers.peek().at == now)
    }

    fun launchRoot(path: String, addition: CoroutineContext, block: suspend RuntimeScope.() -> Unit) {
        root.launch(context = PathElement(path) + addition, block = block)
    }

    fun dispatch(continuation: Continuation<Unit>) { enqueue { continuation.resume(Unit) } }
    internal fun enqueue(action: () -> Unit) { ready.addLast(action) }

    internal fun <T> inJob(owner: TaskJob<*>, action: () -> T): T {
        val previous = currentJob
        currentJob = owner
        return try { action() } finally { currentJob = previous }
    }

    fun hasUnfinishedJobs(): Boolean = root.hasChildren()

    fun schedule(after: Long, action: () -> Unit): TimerHandle {
        val handle = TimerHandle(now + after, nextTimerOrder++, action)
        if (after == 0L) enqueue(handle::fire)
        else timers.add(handle)
        return handle
    }

    internal fun record(context: CoroutineContext, event: String) {
        output(now, context[PathElement]?.path ?: "api", event)
    }
}
