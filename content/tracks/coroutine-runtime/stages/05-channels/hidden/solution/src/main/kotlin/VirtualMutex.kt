import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine

private class LockWaiter(val owner: TaskJob<*>, val pending: Pending<Unit>) {
    var queued = true
    var granted = false
}

class VirtualMutex(private val runtime: VirtualRuntime) : RuntimeMutex {
    private var owner: TaskJob<*>? = null
    private val waiters = ArrayDeque<LockWaiter>()

    override suspend fun lock() {
        val caller = coroutineContext[JobElement]?.owner ?: error("Missing active Job")
        caller.ensureActive()
        if (owner == null) { owner = caller; return }
        if (owner === caller) throw JobFailure("MUTEX_REENTRANT")
        suspendCoroutine<Unit> { continuation ->
            val pending = Pending(runtime, caller, continuation)
            val waiter = LockWaiter(caller, pending)
            pending.onFinish = {
                waiter.queued = false
                if (waiter.granted && owner === caller) {
                    waiter.granted = false
                    passToNext()
                }
            }
            caller.setPending(pending)
            waiters.addLast(waiter)
        }
    }

    override fun unlock() {
        val caller = runtime.currentJob ?: error("Missing active Job")
        if (owner !== caller) throw JobFailure("MUTEX_OWNER")
        passToNext()
    }

    private fun passToNext() {
        while (waiters.isNotEmpty()) {
            val waiter = waiters.removeFirst()
            if (!waiter.queued) continue
            waiter.queued = false
            waiter.granted = true
            owner = waiter.owner
            runtime.enqueue {
                waiter.granted = false
                waiter.pending.deliver(Unit)
            }
            return
        }
        owner = null
    }
}
