import java.io.InputStream

data class SegmentRequest(
    val segment: String,
    val source: String,
    val start: Int,
    val length: Int,
)

class SegmentReceiver(
    private val sources: TransferSources,
    private val store: SegmentStore,
) {
    fun receive(request: SegmentRequest): String {
        val payload = ByteArray(request.length)
        val received = sources.openAt(request.source, request.start).use { stream ->
            collect(stream, payload)
        }
        if (received != request.length) {
            return "FAILED ${request.segment} EOF"
        }
        store.save(request.segment, payload)
        return "STORED ${request.segment} $received"
    }

    private fun collect(stream: InputStream, payload: ByteArray): Int {
        var received = 0
        while (received < payload.size) {
            val count = stream.read(payload, received, payload.size - received)
            if (count == -1) break
            received += count
        }
        return received
    }
}
