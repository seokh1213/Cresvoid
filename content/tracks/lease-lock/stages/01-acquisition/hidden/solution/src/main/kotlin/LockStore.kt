data class Lease(
    val key: String,
    val client: String,
    val acquisitionId: Long,
    val expiresAt: Long,
)

sealed interface AcquisitionResult {
    data class Acquired(val lease: Lease) : AcquisitionResult
    data class Busy(val lease: Lease) : AcquisitionResult
}

class LockStore {
    private val leases = mutableMapOf<String, Lease>()
    private var lastAcquisitionId = 0L

    // The input sequence supplies atomicity; callers cannot split the condition from the write.
    fun acquire(key: String, client: String, expiresAt: Long): AcquisitionResult {
        val current = leases[key]
        if (current != null) return AcquisitionResult.Busy(current)
        val lease = Lease(key, client, lastAcquisitionId + 1, expiresAt)
        leases[key] = lease
        lastAcquisitionId = lease.acquisitionId
        return AcquisitionResult.Acquired(lease)
    }

    fun release(key: String, acquisitionId: Long): Boolean {
        val current = leases[key] ?: return false
        if (current.acquisitionId != acquisitionId) return false
        leases.remove(key)
        return true
    }

    fun find(key: String): Lease? = leases[key]
}
