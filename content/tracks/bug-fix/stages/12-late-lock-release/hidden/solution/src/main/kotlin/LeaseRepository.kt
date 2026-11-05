internal data class AcquireRequest(
    val resource: String,
    val client: String,
    val ttl: Long,
)

internal data class ReleaseRequest(
    val resource: String,
    val client: String,
    val number: Long,
)

internal data class Lease(
    var client: String,
    var number: Long,
    var expiresAt: Long,
)

internal sealed interface Acquisition {
    data class Granted(val lease: Lease) : Acquisition
    data class Busy(val lease: Lease) : Acquisition
}

internal class LeaseRepository {
    private val leases = mutableMapOf<String, Lease>()
    private var lastNumber = 0L

    fun acquire(request: AcquireRequest, now: Long): Acquisition {
        val occupied = current(request.resource, now)
        if (occupied != null) return Acquisition.Busy(occupied)
        val granted = leases[request.resource] ?: Lease(request.client, 0, 0)
        granted.client = request.client
        granted.number = lastNumber + 1
        granted.expiresAt = now + request.ttl
        leases[request.resource] = granted
        lastNumber = granted.number
        return Acquisition.Granted(granted)
    }

    fun release(request: ReleaseRequest, now: Long): Boolean {
        val held = current(request.resource, now) ?: return false
        if (held.client != request.client || held.number != request.number) return false
        return leases.remove(request.resource, held)
    }

    fun current(resource: String, now: Long): Lease? {
        val held = leases[resource] ?: return null
        return held.takeIf { now < it.expiresAt }
    }
}
