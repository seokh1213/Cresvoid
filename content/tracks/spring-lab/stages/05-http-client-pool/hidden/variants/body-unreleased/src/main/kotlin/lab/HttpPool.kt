package lab

import java.io.BufferedInputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PoolWaitTimeout : TimeoutException()

private class Connection(host: String, port: Int) : AutoCloseable {
    val socket = Socket()
    val input: BufferedInputStream

    init {
        try {
            socket.connect(InetSocketAddress(host, port), 2000)
            socket.soTimeout = 5000
            socket.tcpNoDelay = true
            input = BufferedInputStream(socket.getInputStream())
        } catch (error: Exception) {
            socket.close()
            throw error
        }
    }

    fun line(): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size < 8192) {
            val next = input.read()
            if (next < 0) throw EOFException()
            if (next == 10) return bytes.toByteArray().toString(Charsets.US_ASCII).trimEnd('\r')
            bytes.add(next.toByte())
        }
        error("Header exceeds fixture contract")
    }

    override fun close() = socket.close()
}

class PooledBody internal constructor(
    val status: Int,
    private var remaining: Int,
    private val input: BufferedInputStream,
    private val release: (Boolean) -> Unit,
) : AutoCloseable {
    private var closed = false

    fun readByte(): Int {
        check(!closed)
        if (remaining == 0) return -1
        val byte = input.read()
        if (byte < 0) throw EOFException()
        remaining--
        return byte
    }

    fun readText(): String {
        check(!closed)
        val bytes = input.readNBytes(remaining)
        if (bytes.size != remaining) throw EOFException()
        remaining = 0
        return bytes.toString(Charsets.UTF_8)
    }

    override fun close() {
        if (closed) return
        closed = true
        var reusable = false
        try {
            // Drain the framed body before the socket can serve another request.
            reusable = input.readNBytes(remaining).size == remaining
            remaining = 0
        } finally {
            release(reusable)
        }
    }
}

class HttpPool(private val settings: PoolSettings) : AutoCloseable {
    private val host = System.getenv("UPSTREAM_HOST") ?: System.getProperty("lab.upstream.host")
    private val port = (System.getenv("UPSTREAM_PORT") ?: System.getProperty("lab.upstream.port")).toInt()
    private val lock = ReentrantLock(true)
    private val available = lock.newCondition()
    private val idle = ArrayDeque<Connection>()
    private val all = mutableSetOf<Connection>()
    private var leased = 0
    private var pending = 0
    private var opened = 0
    private var timeouts = 0
    private var peak = 0

    init {
        require(settings.maxTotal > 0 && settings.maxPerRoute > 0 && settings.waitMillis > 0)
    }

    private fun acquire(): Connection = lock.withLock {
        val deadline = System.nanoTime() + Duration.ofMillis(settings.waitMillis).toNanos()
        pending++
        try {
            while (leased >= minOf(settings.maxTotal, settings.maxPerRoute)) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) {
                    timeouts++
                    throw PoolWaitTimeout()
                }
                available.awaitNanos(remaining)
            }
            val connection = if (idle.isEmpty()) {
                Connection(host, port).also { all.add(it); opened++ }
            } else {
                idle.removeFirst()
            }
            leased++
            peak = maxOf(peak, leased)
            connection
        } finally {
            pending--
        }
    }

    private fun release(connection: Connection, reusable: Boolean) = lock.withLock {
        if (reusable) idle.addLast(connection) else {
            all.remove(connection)
            connection.close()
        }
        leased--
        available.signalAll()
    }

    fun get(path: String): PooledBody {
        val connection = acquire()
        try {
            val request = "GET $path HTTP/1.1\r\nHost: $host:$port\r\nConnection: keep-alive\r\n\r\n"
            connection.socket.getOutputStream().apply {
                write(request.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val status = connection.line().split(' ')[1].toInt()
            var length: Int? = null
            var header = connection.line()
            while (header.isNotEmpty()) {
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    length = header.substringAfter(':').trim().toInt()
                }
                header = connection.line()
            }
            return PooledBody(status, requireNotNull(length), connection.input) { reusable ->
                release(connection, reusable)
            }
        } catch (error: Exception) {
            release(connection, false)
            throw error
        }
    }

    fun metrics(): String = lock.withLock {
        "leased=$leased\npending=$pending\nopened=$opened\npeak=$peak\ntimeouts=$timeouts\n" +
            "maxTotal=${settings.maxTotal}\nmaxPerRoute=${settings.maxPerRoute}\nwaitMillis=${settings.waitMillis}\n"
    }

    override fun close() = lock.withLock {
        all.forEach { it.close() }
        all.clear()
        idle.clear()
    }
}
