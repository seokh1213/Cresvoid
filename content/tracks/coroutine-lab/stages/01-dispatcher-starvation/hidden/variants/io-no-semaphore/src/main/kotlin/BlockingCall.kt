import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class BlockingCall {
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val started = AtomicInteger()
    private val completed = AtomicInteger()
    private val active = AtomicInteger()
    private val maxActive = AtomicInteger()

    suspend fun execute() {
        withContext(ioDispatcher) {
            started.incrementAndGet()
            val nowActive = active.incrementAndGet()
            maxActive.accumulateAndGet(nowActive, ::maxOf)
            try {
                Thread.sleep(300)
            } finally {
                active.decrementAndGet()
                completed.incrementAndGet()
            }
        }
    }

    fun stats(): String = buildString {
        appendLine("started=${started.get()}")
        appendLine("completed=${completed.get()}")
        appendLine("active=${active.get()}")
        appendLine("max_active=${maxActive.get()}")
    }
}
