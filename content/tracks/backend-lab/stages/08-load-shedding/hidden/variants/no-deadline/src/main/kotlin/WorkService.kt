import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class WorkService {
    private val active = AtomicInteger()
    private val waiting = AtomicInteger()
    private val maxActive = AtomicInteger()
    private val maxWaiting = AtomicInteger()
    private val started = AtomicLong()
    private val completed = AtomicLong()
    private val expiredStarted = AtomicLong()

    fun perform(deadlineNanos: Long) {
        if (System.nanoTime() >= deadlineNanos) expiredStarted.incrementAndGet()
        started.incrementAndGet()
        val current = active.incrementAndGet()
        maxActive.accumulateAndGet(current, ::maxOf)
        try {
            Thread.sleep(250)
            completed.incrementAndGet()
        } finally {
            active.decrementAndGet()
        }
    }

    fun awaitSlot(acquire: () -> Boolean): Boolean {
        val current = waiting.incrementAndGet()
        maxWaiting.accumulateAndGet(current, ::maxOf)
        return try {
            acquire()
        } finally {
            waiting.decrementAndGet()
        }
    }

    fun calibrate(): Double {
        val began = System.nanoTime()
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val tasks = List(16) {
                executor.submit(Callable {
                    repeat(8) { perform(Long.MAX_VALUE) }
                })
            }
            tasks.forEach { it.get() }
        }
        return 128.0 / ((System.nanoTime() - began) / 1e9)
    }

    fun reset() {
        check(active.get() == 0 && waiting.get() == 0)
        maxActive.set(0)
        maxWaiting.set(0)
        started.set(0)
        completed.set(0)
        expiredStarted.set(0)
    }

    fun snapshot(): String = listOf(
        "active=${active.get()}", "waiting=${waiting.get()}",
        "max_active=${maxActive.get()}", "max_waiting=${maxWaiting.get()}",
        "started=${started.get()}", "completed=${completed.get()}",
        "expired_started=${expiredStarted.get()}",
    ).joinToString("\n")
}
