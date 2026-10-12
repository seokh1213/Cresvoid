private const val FNV_OFFSET_BASIS = 2_166_136_261L
private const val FNV_PRIME = 16_777_619L
private const val UNSIGNED_32_BITS = 0xFFFF_FFFFL

fun fnv1aHash(key: String): Long =
    key.encodeToByteArray().fold(FNV_OFFSET_BASIS) { hash, byte ->
        ((hash xor (byte.toLong() and 0xFF)) * FNV_PRIME) and UNSIGNED_32_BITS
    }

fun keyedPartition(key: String, partitionCount: Int): Int {
    val signedHash = fnv1aHash(key).toInt()
    return (signedHash and Int.MAX_VALUE) % partitionCount
}

class StickyPartition(private val batchSize: Long) {
    var partition: Int = 0
        private set
    private var accumulatedBytes: Long = 0

    fun recordAppended(sizeBytes: Long, partitionCount: Int) {
        accumulatedBytes += sizeBytes
        if (accumulatedBytes >= batchSize) {
            partition = (partition + 1) % partitionCount
            accumulatedBytes = 0
        }
    }
}
