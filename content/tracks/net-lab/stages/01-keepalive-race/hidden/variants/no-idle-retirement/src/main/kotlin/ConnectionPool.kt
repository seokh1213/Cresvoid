import java.io.IOException

class ConnectionPool(private val upstreamPort: Int) : AutoCloseable {
    private var connection: UpstreamConnection? = null

    @Synchronized
    fun execute(method: String, target: String): RelayReply {
        val maxAttempts = if (method == "GET") 2 else 1
        repeat(maxAttempts) { attempt ->
            try {
                val active = connection ?: UpstreamConnection(upstreamPort).also { connection = it }
                val reply = active.exchange(method, target)
                return reply
            } catch (_: IOException) {
                // A failed write or read cannot prove that the upstream did not apply the request.
                discard()
                if (attempt == maxAttempts - 1) return RelayReply(502, "upstream-unavailable")
            }
        }
        error("Attempt budget exhausted")
    }

    private fun discard() {
        runCatching { connection?.close() }
        connection = null
    }

    @Synchronized
    override fun close() = discard()
}
