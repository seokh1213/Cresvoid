const val DEFAULT_RETENTION_MS = 604_800_000L
const val DEFAULT_SEGMENT_BYTES = 1_073_741_824L
const val DEFAULT_BATCH_SIZE = 16_384L
const val DEFAULT_MIN_CLEANABLE_DIRTY_PERCENT = 50
const val DEFAULT_DELETE_RETENTION_MS = 86_400_000L

class TopicConfig(
    val segmentBytes: Long,
    val batchSize: Long,
    val cleanupPolicy: CleanupPolicy,
)

sealed interface CleanupPolicy {
    data class Delete(val retentionMs: Long?, val retentionBytes: Long?) : CleanupPolicy

    data class Compact(val minCleanableDirtyPercent: Int, val deleteRetentionMs: Long) : CleanupPolicy
}
