const val DEFAULT_SESSION_TIMEOUT_MS = 45_000L
const val DEFAULT_MAX_POLL_INTERVAL_MS = 300_000L

class GroupConfig(
    val sessionTimeoutMs: Long,
    val maxPollIntervalMs: Long,
    val autoOffsetReset: OffsetResetPolicy,
)

sealed interface OffsetResetPolicy {
    fun positionIn(log: PartitionLog): Long

    data object Earliest : OffsetResetPolicy {
        override fun positionIn(log: PartitionLog): Long = log.logStartOffset
    }

    data object Latest : OffsetResetPolicy {
        override fun positionIn(log: PartitionLog): Long = log.logEndOffset
    }
}
