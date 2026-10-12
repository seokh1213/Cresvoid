const val DEFAULT_RETENTION_MS = 604_800_000L
const val DEFAULT_SEGMENT_BYTES = 1_073_741_824L
const val DEFAULT_BATCH_SIZE = 16_384L

class TopicConfig(
    val retentionMs: Long?,
    val retentionBytes: Long?,
    val segmentBytes: Long,
    val batchSize: Long,
)
