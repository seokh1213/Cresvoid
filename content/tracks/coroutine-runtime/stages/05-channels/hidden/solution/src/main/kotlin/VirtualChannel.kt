import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine

private class WaitingSender<T>(val value: T, val pending: Pending<Unit>) { var queued = true }
private class WaitingReceiver<T>(val pending: Pending<T>) { var queued = true }

class VirtualChannel<T>(private val runtime: VirtualRuntime, private val capacity: Int) : RuntimeChannel<T> {
    private val buffer = ArrayDeque<T>()
    private val senders = ArrayDeque<WaitingSender<T>>()
    private val receivers = ArrayDeque<WaitingReceiver<T>>()
    private var closed = false

    override suspend fun send(value: T) {
        val caller = coroutineContext[JobElement]?.owner ?: error("Missing active Job")
        caller.ensureActive()
        if (closed) throw JobFailure("CHANNEL_CLOSED")
        val receiver = nextReceiver()
        if (receiver != null) {
            runtime.enqueue { receiver.pending.deliver(value) }
        } else if (buffer.size < capacity) {
            buffer.addLast(value)
        } else {
            suspendCoroutine<Unit> { continuation ->
                val pending = Pending(runtime, caller, continuation)
                val waiter = WaitingSender(value, pending)
                pending.onFinish = { waiter.queued = false }
                caller.setPending(pending)
                senders.addLast(waiter)
            }
        }
    }

    override suspend fun receive(): T {
        val caller = coroutineContext[JobElement]?.owner ?: error("Missing active Job")
        caller.ensureActive()
        if (buffer.isNotEmpty()) {
            val value = buffer.removeFirst()
            nextSender()?.let { sender ->
                buffer.addLast(sender.value)
                runtime.enqueue { sender.pending.deliver(Unit) }
            }
            return value
        }
        val sender = nextSender()
        if (sender != null) {
            runtime.enqueue { sender.pending.deliver(Unit) }
            return sender.value
        }
        if (closed) throw JobFailure("CHANNEL_CLOSED")
        return suspendCoroutine { continuation ->
            val pending = Pending(runtime, caller, continuation)
            val waiter = WaitingReceiver(pending)
            pending.onFinish = { waiter.queued = false }
            caller.setPending(pending)
            receivers.addLast(waiter)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        while (senders.isNotEmpty()) {
            val sender = senders.removeFirst()
            if (!sender.queued) continue
            sender.queued = false
            runtime.enqueue { sender.pending.deliverResult(Result.failure(JobFailure("CHANNEL_CLOSED"))) }
        }
        while (receivers.isNotEmpty()) {
            val receiver = receivers.removeFirst()
            if (!receiver.queued) continue
            receiver.queued = false
            runtime.enqueue { receiver.pending.deliverResult(Result.failure(JobFailure("CHANNEL_CLOSED"))) }
        }
    }

    private fun nextSender(): WaitingSender<T>? {
        while (senders.isNotEmpty()) {
            val sender = senders.removeFirst()
            if (sender.queued) { sender.queued = false; return sender }
        }
        return null
    }

    private fun nextReceiver(): WaitingReceiver<T>? {
        while (receivers.isNotEmpty()) {
            val receiver = receivers.removeFirst()
            if (receiver.queued) { receiver.queued = false; return receiver }
        }
        return null
    }
}
