import java.io.IOException

class ConnectionPool(private val upstreamPort: Int) : AutoCloseable {
    private var connection: UpstreamConnection? = null
    private var lastCompletedNanos = 0L
    private val idleLimitNanos = 3_500_000_000L

    @Synchronized
    fun execute(method: String, target: String): RelayReply {
        if (connection != null && System.nanoTime() - lastCompletedNanos >= idleLimitNanos) {
            discard()
        }
        val maxAttempts = if (method == "GET") 2 else 1
        repeat(maxAttempts) { attempt ->
            try {
                val active = connection ?: UpstreamConnection(upstreamPort).also { connection = it }
                val reply = active.exchange(method, target)
                lastCompletedNanos = System.nanoTime()
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
