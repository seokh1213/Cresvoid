import java.lang.ref.Reference
import java.nio.ByteBuffer

const val MIB: Long = 1_048_576L

data class ProbeRequest(val heapBytes: Long, val directBytes: Long, val budgetBytes: Long)

fun measure(request: ProbeRequest): String {
    val heap = try {
        ByteArray(request.heapBytes.toInt())
    } catch (_: OutOfMemoryError) {
        null
    }
    val direct = try {
        ByteBuffer.allocateDirect(request.directBytes.toInt())
    } catch (_: OutOfMemoryError) {
        null
    }
    val heapRetained = heap?.size?.toLong() ?: 0L
    val directRetained = direct?.capacity()?.toLong() ?: 0L
    val maxHeap = Runtime.getRuntime().totalMemory()
    val budgetExceeded = heapRetained + directRetained > request.budgetBytes
    val result = listOf(
        "max_heap_bytes=$maxHeap",
        "heap_requested_bytes=${request.heapBytes}",
        "heap_retained_bytes=$heapRetained",
        "direct_requested_bytes=${request.directBytes}",
        "direct_retained_bytes=$directRetained",
        "heap_oom=${if (heap == null) 1 else 0}",
        "direct_oom=${if (direct == null) 1 else 0}",
        "budget_exceeded=${if (budgetExceeded) 1 else 0}",
    ).joinToString("\n")
    // Keep both allocations alive until their combined size has been measured.
    Reference.reachabilityFence(heap)
    Reference.reachabilityFence(direct)
    return result
}
