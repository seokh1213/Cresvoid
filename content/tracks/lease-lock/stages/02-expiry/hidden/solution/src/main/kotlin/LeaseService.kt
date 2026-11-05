class LeaseService(private val store: LockStore) {
    private var now = 0L

    fun execute(command: Command): String? = when (command) {
        is Command.Acquire -> acquire(command)
        is Command.Release -> if (store.release(command.key, command.acquisitionId)) "RELEASED" else "DENIED"
        is Command.Status -> store.find(command.key)?.let { held(it) } ?: "FREE"
        is Command.Advance -> advance(command)
        is Command.Renew -> renew(command)
        Command.Ignore -> null
        Command.Invalid -> "ERROR"
    }

    private fun acquire(command: Command.Acquire): String =
        when (val result = store.acquire(command.key, command.client, now + command.ttl)) {
            is AcquisitionResult.Acquired -> "ACQUIRED ${result.lease.acquisitionId} ${result.lease.expiresAt}"
            is AcquisitionResult.Busy -> with(result.lease) { "BUSY $acquisitionId $client $expiresAt" }
        }

    private fun held(lease: Lease): String = with(lease) { "HELD $acquisitionId $client $expiresAt" }

    private fun advance(command: Command.Advance): String {
        if (command.time < now) return "ERROR"
        val expiredCount = store.expireThrough(command.time)
        now = command.time
        return "ADVANCED $now $expiredCount"
    }

    private fun renew(command: Command.Renew): String {
        val lease = store.renew(command.key, command.acquisitionId, now, now + command.ttl) ?: return "DENIED"
        return "RENEWED ${lease.acquisitionId} ${lease.expiresAt}"
    }
}
