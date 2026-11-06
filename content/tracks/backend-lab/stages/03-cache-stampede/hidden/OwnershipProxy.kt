import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OwnershipProxy(private val upstreamPort: Int, private val successor: String) : AutoCloseable {
    private val listener = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
    private val workers = Executors.newVirtualThreadPerTaskExecutor()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val failures = ConcurrentHashMap.newKeySet<Throwable>()
    private val armed = AtomicBoolean()
    val replaced = AtomicBoolean()
    val port: Int get() = listener.localPort

    init {
        workers.submit {
            while (!listener.isClosed) {
                try {
                    val downstream = listener.accept()
                    sockets.add(downstream)
                    workers.submit { forward(downstream) }
                } catch (error: SocketException) {
                    if (!listener.isClosed) failures.add(error)
                }
            }
        }
    }

    fun arm() { armed.set(true) }

    fun successorSurvived(redis: RedisClient): Boolean = redis.get("lock:product:93") == successor

    private fun forward(downstream: Socket) {
        try {
            downstream.use { client ->
                Socket("127.0.0.1", upstreamPort).use { upstream ->
                    sockets.add(upstream)
                    relay(client, upstream)
                }
            }
        } catch (_: EOFException) {
        } catch (error: Exception) {
            if (!listener.isClosed) failures.add(error)
        } finally {
            sockets.remove(downstream)
        }
    }

    private fun relay(client: Socket, upstream: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())
        val originInput = BufferedInputStream(upstream.getInputStream())
        val originOutput = BufferedOutputStream(upstream.getOutputStream())
        while (!listener.isClosed) {
            val command = respFrame(input)
            originOutput.write(command)
            originOutput.flush()
            val reply = respFrame(originInput)
            // Change ownership after GET has read the old value, before the application can issue DEL.
            if (armed.get() && isLockGet(command) && reply.first().toInt() == '$'.code &&
                !reply.toString(Charsets.UTF_8).startsWith("\$-1\r\n") && replaced.compareAndSet(false, true)) {
                RedisClient("127.0.0.1", upstreamPort).use { redis ->
                    redis.set("lock:product:93", successor, expireMillis = 5_000)
                }
            }
            output.write(reply)
            output.flush()
        }
    }

    private fun isLockGet(command: ByteArray): Boolean =
        command.toString(Charsets.UTF_8).split("\r\n").let { parts ->
            parts.size == 6 && parts[0] == "*2" && parts[2].equals("GET", ignoreCase = true) &&
                parts[4] == "lock:product:93"
        }

    override fun close() {
        listener.close()
        sockets.forEach { it.close() }
        workers.shutdownNow()
        check(workers.awaitTermination(5, TimeUnit.SECONDS)) { "프록시 종료 시간 초과" }
        check(failures.isEmpty()) { "프록시 오류: ${failures.first()}" }
    }
}

private fun respFrame(input: InputStream): ByteArray {
    val bytes = ByteArrayOutputStream()
    fun readLine(): String {
        val line = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) throw EOFException()
            bytes.write(byte)
            if (byte == '\r'.code) {
                check(input.read() == '\n'.code) { "잘못된 RESP 줄 끝" }
                bytes.write('\n'.code)
                return line.toString()
            }
            line.append(byte.toChar())
        }
    }
    val header = readLine()
    when (header.first()) {
        '$' -> {
            val length = header.substring(1).toInt()
            if (length >= 0) {
                val body = input.readNBytes(length + 2)
                if (body.size != length + 2) throw EOFException()
                bytes.write(body)
            }
        }
        '*' -> repeat(header.substring(1).toInt().coerceAtLeast(0)) { bytes.write(respFrame(input)) }
        '+', '-', ':' -> Unit
        else -> error("지원하지 않는 RESP 형식")
    }
    return bytes.toByteArray()
}
