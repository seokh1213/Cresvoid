import java.io.IOException

class ConnectionPool(private val upstreamPort: Int) : AutoCloseable {
    private var connection: UpstreamConnection? = null

    @Synchronized
    fun execute(method: String, target: String): RelayReply {
        return try {
            val active = connection ?: UpstreamConnection(upstreamPort).also { connection = it }
            active.exchange(method, target)
        } catch (_: IOException) {
            discard()
            RelayReply(502, "upstream-unavailable")
        }
    }

    private fun discard() {
        runCatching { connection?.close() }
        connection = null
    }

    @Synchronized
    override fun close() = discard()
}
