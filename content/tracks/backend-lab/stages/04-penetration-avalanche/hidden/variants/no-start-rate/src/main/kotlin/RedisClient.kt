import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A small Redis client over RESP2 using only the JDK. Labs copy this file unchanged into starter and solution projects
 * so learners spend their time on caching behavior, not on a client library.
 */
class RedisClient(
    private val host: String,
    private val port: Int,
    poolSize: Int = 16,
    private val timeoutMillis: Int = 2_000,
) : Closeable {
    private val idle = ArrayBlockingQueue<Connection>(poolSize)
    private val permits = java.util.concurrent.Semaphore(poolSize)

    fun get(key: String): String? = call("GET", key) as String?

    /** SET with an optional expiry in milliseconds. Returns false when [onlyIfAbsent] is set and the key exists. */
    fun set(key: String, value: String, expireMillis: Long? = null, onlyIfAbsent: Boolean = false): Boolean {
        val arguments = mutableListOf("SET", key, value)
        if (expireMillis != null) arguments += listOf("PX", expireMillis.toString())
        if (onlyIfAbsent) arguments += "NX"
        return call(*arguments.toTypedArray()) == "OK"
    }

    fun delete(vararg keys: String): Long = call("DEL", *keys) as Long

    fun increment(key: String): Long = call("INCR", key) as Long

    fun expireMillis(key: String, millis: Long): Boolean = call("PEXPIRE", key, millis.toString()) == 1L

    fun ttlMillis(key: String): Long = call("PTTL", key) as Long

    /** Any command. Replies are String, Long, null, or List of those. Redis errors throw [RedisError]. */
    fun call(vararg arguments: String): Any? {
        if (!permits.tryAcquire(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)) throw IOException("Redis 연결을 기다리다 시간이 지났습니다")
        var connection: Connection? = null
        try {
            connection = idle.poll() ?: Connection(host, port, timeoutMillis)
            val reply = connection.call(arguments)
            idle.offer(connection)
            connection = null
            return reply
        } finally {
            connection?.close()
            permits.release()
        }
    }

    override fun close() {
        generateSequence { idle.poll() }.forEach(Connection::close)
    }
}

class RedisError(message: String) : RuntimeException(message)

private class Connection(host: String, port: Int, timeoutMillis: Int) : Closeable {
    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), timeoutMillis)
        soTimeout = timeoutMillis
        tcpNoDelay = true
    }
    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    fun call(arguments: Array<out String>): Any? {
        output.write("*${arguments.size}\r\n".toByteArray())
        for (argument in arguments) {
            val bytes = argument.toByteArray(Charsets.UTF_8)
            output.write("\$${bytes.size}\r\n".toByteArray())
            output.write(bytes)
            output.write("\r\n".toByteArray())
        }
        output.flush()
        return read()
    }

    private fun read(): Any? {
        val type = input.read()
        val line = readLine()
        return when (type.toChar()) {
            '+' -> line
            '-' -> throw RedisError(line)
            ':' -> line.toLong()
            '$' -> line.toInt().let { length -> if (length < 0) null else readBulk(length) }
            '*' -> line.toInt().let { count -> if (count < 0) null else List(count) { read() } }
            else -> throw IOException("알 수 없는 Redis 응답: ${type.toChar()}$line")
        }
    }

    private fun readBulk(length: Int): String {
        val bytes = input.readNBytes(length)
        readLine()
        return String(bytes, Charsets.UTF_8)
    }

    private fun readLine(): String {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) throw IOException("Redis 연결이 끊겼습니다")
            if (byte == '\r'.code) {
                input.read()
                return line.toString()
            }
            line.append(byte.toChar())
        }
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }
}
