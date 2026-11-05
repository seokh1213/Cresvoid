import java.util.ArrayDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class RefreshInbox {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val monitor = Object()
    private val pending = ArrayDeque<Product>()
    private var closed = false

    fun submit(products: List<Product>) = synchronized(monitor) {
        check(!closed)
        pending.addAll(products)
        if (products.isNotEmpty()) monitor.notifyAll()
    }

    fun take(): Product? = synchronized(monitor) {
        if (pending.isEmpty() && !closed) {
            monitor.wait()
        }
        if (closed && pending.isEmpty()) null else pending.removeFirst()
    }

    fun isEmpty(): Boolean = synchronized(monitor) { pending.isEmpty() }

    fun close() = synchronized(monitor) {
        closed = true
        monitor.notifyAll()
    }
}

class RefreshWorkers(private val workerCount: Int, private val catalog: ProductCatalog) {
    private val inbox = RefreshInbox()
    private val completed = AtomicInteger()
    private val errors = ConcurrentLinkedQueue<String>()

    fun execute(products: List<Product>): RefreshReport {
        val workers = List(workerCount) { number ->
            Thread({ consume() }, "catalog-index-${number + 1}").apply { isDaemon = true }
        }
        try {
            workers.forEach { it.start() }
            awaitState { workers.all { it.state == Thread.State.WAITING } }
            inbox.submit(products)
            awaitState { settled(workers) }
            return RefreshReport(completed.get(), workers.count { it.isAlive }, errors.sorted())
        } finally {
            inbox.close()
            stopWorkers(workers)
        }
    }

    private fun consume() {
        try {
            while (true) {
                val product = inbox.take() ?: return
                catalog.index(product)
                completed.incrementAndGet()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (failure: RuntimeException) {
            errors.add(failure.javaClass.simpleName)
        }
    }

    private fun settled(workers: List<Thread>): Boolean {
        if (workers.all { it.state == Thread.State.TERMINATED }) return true
        return inbox.isEmpty() && workers.all {
            it.state == Thread.State.WAITING || it.state == Thread.State.TERMINATED
        }
    }

    private fun awaitState(reached: () -> Boolean) {
        // The deadline bounds a broken run; thread states, rather than elapsed time, release the check.
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!reached()) {
            check(System.nanoTime() < deadline) { "Worker state check timed out" }
            Thread.yield()
        }
    }

    private fun stopWorkers(workers: List<Thread>) {
        workers.forEach { it.join(1_000) }
        val remaining = workers.filter { it.isAlive }
        remaining.forEach { it.interrupt() }
        remaining.forEach { it.join(1_000) }
        check(workers.none { it.isAlive }) { "Workers did not stop" }
    }
}
