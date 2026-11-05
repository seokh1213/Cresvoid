import java.util.PriorityQueue

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
    private val expirations = PriorityQueue(compareBy<Lease> { it.expiresAt }.thenBy { it.acquisitionId })
    private var lastAcquisitionId = 0L

    // The input sequence supplies atomicity; callers cannot split the condition from the write.
    fun acquire(key: String, client: String, expiresAt: Long): AcquisitionResult {
        val current = leases[key]
        if (current != null) return AcquisitionResult.Busy(current)
        val lease = Lease(key, client, lastAcquisitionId + 1, expiresAt)
        leases[key] = lease
        expirations.add(lease)
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

    fun renew(key: String, acquisitionId: Long, now: Long, expiresAt: Long): Lease? {
        val current = leases[key] ?: return null
        if (current.acquisitionId != acquisitionId || current.expiresAt <= now) return null
        val renewed = current.copy(expiresAt = expiresAt)
        leases[key] = renewed
        expirations.add(renewed)
        return renewed
    }

    fun expireThrough(time: Long): Int {
        var expiredCount = 0
        while (expirations.isNotEmpty() && expirations.peek().expiresAt <= time) {
            val candidate = expirations.remove()
            val current = leases[candidate.key] ?: continue
            // Renewals and reacquisitions leave old candidates that no longer own this key.
            if (current.acquisitionId != candidate.acquisitionId || current.expiresAt != candidate.expiresAt) continue
            leases.remove(candidate.key)
            expiredCount++
        }
        return expiredCount
    }
}
