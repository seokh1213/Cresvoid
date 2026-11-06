package lab

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class IdleProxy(private val databasePort: Int) : AutoCloseable {
    private val listener = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val sessions = ConcurrentHashMap<Long, Relay>()
    private val closed = AtomicBoolean()
    val created = AtomicLong()
    val idleDrops = AtomicLong()
    val ageDrops = AtomicLong()
    val port: Int get() = listener.localPort

    init {
        workers.submit { acceptConnections() }
        timer.scheduleAtFixedRate(::expireConnections, 100, 100, TimeUnit.MILLISECONDS)
    }

    private fun acceptConnections() {
        while (!closed.get()) {
            val client = try {
                listener.accept()
            } catch (error: java.io.IOException) {
                if (!closed.get()) throw error
                return
            }
            try {
                val upstream = Socket("127.0.0.1", databasePort)
                val id = created.incrementAndGet()
                val relay = Relay(id, client, upstream)
                sessions[id] = relay
                workers.submit { relay.forward(client, upstream) }
                workers.submit { relay.forward(upstream, client) }
            } catch (_: java.io.IOException) {
                client.close()
            }
        }
    }

    private fun expireConnections() {
        val now = System.nanoTime()
        for (relay in sessions.values) {
            when {
                now - relay.lastActivity.get() >= TimeUnit.SECONDS.toNanos(60) -> {
                    if (relay.closeOnce()) idleDrops.incrementAndGet()
                }
                now - relay.openedAt >= TimeUnit.SECONDS.toNanos(75) -> {
                    if (relay.closeOnce()) ageDrops.incrementAndGet()
                }
            }
        }
    }

    private inner class Relay(val id: Long, val client: Socket, val upstream: Socket) {
        val openedAt = System.nanoTime()
        val lastActivity = AtomicLong(openedAt)
        private val ended = AtomicBoolean()

        fun forward(source: Socket, target: Socket) {
            try {
                val buffer = ByteArray(8_192)
                val input = source.getInputStream()
                val output = target.getOutputStream()
                while (!ended.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    lastActivity.set(System.nanoTime())
                    output.write(buffer, 0, count)
                    output.flush()
                }
            } catch (_: java.io.IOException) {
                // Closing either direction must release both blocked reads.
            } finally {
                closeOnce()
            }
        }

        fun closeOnce(): Boolean {
            if (!ended.compareAndSet(false, true)) return false
            sessions.remove(id)
            runCatching { client.close() }
            runCatching { upstream.close() }
            return true
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        listener.close()
        timer.shutdownNow()
        sessions.values.forEach { it.closeOnce() }
        workers.shutdownNow()
        workers.awaitTermination(3, TimeUnit.SECONDS)
    }
}
