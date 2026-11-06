import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class RelayReply(val status: Int, val body: String)

class UpstreamConnection(port: Int) : AutoCloseable {
    private val socket = Socket().apply {
        connect(InetSocketAddress("127.0.0.1", port), 1_000)
        soTimeout = 2_000
        tcpNoDelay = true
    }
    private val input = socket.getInputStream().buffered()
    private val output = socket.getOutputStream()

    fun exchange(method: String, target: String): RelayReply {
        val request = "$method $target HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\n\r\n"
        output.write(request.toByteArray(Charsets.US_ASCII))
        output.flush()
        val statusLine = readLine()
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IOException("Invalid HTTP status")
        var length: Int? = null
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            if (header.substringBefore(':').equals("Content-Length", ignoreCase = true)) {
                length = header.substringAfter(':').trim().toIntOrNull()
            }
        }
        val size = length?.takeIf { it in 0..65_536 } ?: throw IOException("Invalid body length")
        val body = input.readNBytes(size)
        if (body.size != size) throw EOFException("Incomplete response body")
        return RelayReply(status, body.toString(Charsets.UTF_8))
    }

    private fun readLine(): String {
        val line = StringBuilder()
        while (line.length < 8_192) {
            val next = input.read()
            if (next < 0) throw EOFException("Connection closed before complete response")
            if (next == 10) return line.toString().removeSuffix("\r")
            line.append(next.toChar())
        }
        throw IOException("Header line too long")
    }

    override fun close() = socket.close()
}
