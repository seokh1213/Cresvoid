package lab

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private class ManualSource : AutoCloseable {
    private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val closed = AtomicBoolean()
    val port: Int get() = server.localPort

    init {
        executor.submit {
            try {
                while (!closed.get()) {
                    val socket = server.accept()
                    sockets.add(socket)
                    executor.submit { serve(socket) }
                }
            } catch (error: IOException) {
                if (!closed.get()) throw error
            }
        }
    }

    private fun serve(socket: Socket) {
        try {
            socket.use {
                val input = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                while (!closed.get()) {
                    val request = input.readLine() ?: return
                    while (!input.readLine().isNullOrEmpty()) { }
                    val query = request.substringAfter('?').substringBefore(' ').split('&').associate {
                        it.substringBefore('=') to it.substringAfter('=')
                    }
                    val mode = query.getValue("mode")
                    Thread.sleep(if (mode == "hold") 2000 else 100)
                    val bytes = (query.getValue("id") + "|" + "x".repeat(8192)).toByteArray()
                    val status = if (mode == "error") "503 Unavailable" else "200 OK"
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 $status\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
                    if (mode == "eof" || mode == "stall") {
                        output.write(bytes, 0, 1)
                        output.flush()
                        if (mode == "stall") Thread.sleep(30_000)
                        return
                    }
                    output.write(bytes)
                    output.flush()
                }
            }
        } catch (_: IOException) {
            // Clients may discard a connection after a body read fails.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            sockets.remove(socket)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.close()
        sockets.forEach { it.close() }
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}

fun startManualSource() {
    if (System.getenv("UPSTREAM_HOST") != null) return
    val source = ManualSource()
    System.setProperty("lab.upstream.host", "127.0.0.1")
    System.setProperty("lab.upstream.port", source.port.toString())
    Runtime.getRuntime().addShutdownHook(Thread { source.close() })
}
