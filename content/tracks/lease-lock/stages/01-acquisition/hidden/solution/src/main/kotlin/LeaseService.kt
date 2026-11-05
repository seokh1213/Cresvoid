class LeaseService(private val store: LockStore) {
    fun execute(command: Command): String? = when (command) {
        is Command.Acquire -> acquire(command)
        is Command.Release -> if (store.release(command.key, command.acquisitionId)) "RELEASED" else "DENIED"
        is Command.Status -> store.find(command.key)?.let { held(it) } ?: "FREE"
        Command.Ignore -> null
        Command.Invalid -> "ERROR"
    }

    private fun acquire(command: Command.Acquire): String =
        when (val result = store.acquire(command.key, command.client, command.ttl)) {
            is AcquisitionResult.Acquired -> "ACQUIRED ${result.lease.acquisitionId} ${result.lease.expiresAt}"
            is AcquisitionResult.Busy -> with(result.lease) { "BUSY $acquisitionId $client $expiresAt" }
        }

    private fun held(lease: Lease): String = with(lease) { "HELD $acquisitionId $client $expiresAt" }
}
