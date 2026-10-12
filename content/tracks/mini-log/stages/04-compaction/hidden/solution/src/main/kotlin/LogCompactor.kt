class DirtyStats(val firstDirtyOffset: Long, val cleanBytes: Long, val dirtyBytes: Long)

class LogCompactor(private val log: PartitionLog) {
    private var firstDirtyOffset = 0L
    private var cleanBytes = 0L

    // 압축 정책 토픽은 보관 규칙을 받지 않아서 깨끗한 세그먼트는 다음 압축 전까지 바뀌지 않는다
    fun dirtyStats(): DirtyStats = DirtyStats(firstDirtyOffset, cleanBytes, log.closedBytes - cleanBytes)

    fun compactIfDirty(policy: CleanupPolicy.Compact, now: Long) {
        val stats = dirtyStats()
        val cleanableBytes = stats.cleanBytes + stats.dirtyBytes
        if (cleanableBytes > 0 && 100 * stats.dirtyBytes > policy.minCleanableDirtyPercent * cleanableBytes) {
            compact(policy, now)
        }
    }

    private fun compact(policy: CleanupPolicy.Compact, now: Long) {
        val closedSegments = log.closedSegments
        val latestOffsets = latestOffsetsByKey(closedSegments.filter { it.baseOffset >= firstDirtyOffset })
        val cleanedSegments = closedSegments.map { segment ->
            Segment(segment.baseOffset).apply {
                segment.records.mapNotNull { retainedRecord(it, latestOffsets, policy, now) }.forEach(::append)
            }
        }
        log.replaceClosedSegments(merge(cleanedSegments))
        firstDirtyOffset = log.activeSegment.baseOffset
        cleanBytes = log.closedBytes
    }

    private fun latestOffsetsByKey(dirtySegments: List<Segment>): Map<String, Long> {
        val latestOffsets = HashMap<String, Long>()
        for (segment in dirtySegments) {
            for (record in segment.records) record.message.key?.let { latestOffsets[it] = record.offset }
        }
        return latestOffsets
    }

    private fun retainedRecord(
        record: Record,
        latestOffsets: Map<String, Long>,
        policy: CleanupPolicy.Compact,
        now: Long,
    ): Record? {
        val latestOffset = record.message.key?.let(latestOffsets::get)
        if (latestOffset != null && record.offset < latestOffset) return null
        if (!record.message.isTombstone) return record
        val deleteHorizon = record.deleteHorizon ?: return record.withDeleteHorizon(now + policy.deleteRetentionMs)
        return if (now >= deleteHorizon) null else record
    }

    private fun merge(cleanedSegments: List<Segment>): List<Segment> {
        val merged = ArrayList<Segment>()
        for (segment in cleanedSegments) {
            val current = merged.lastOrNull()
            if (current != null && current.sizeBytes + segment.sizeBytes <= log.segmentBytes) {
                segment.records.forEach(current::append)
            } else {
                merged += segment
            }
        }
        return merged
    }
}
