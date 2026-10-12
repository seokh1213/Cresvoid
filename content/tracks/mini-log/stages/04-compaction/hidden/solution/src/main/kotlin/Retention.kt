fun PartitionLog.applyRetention(policy: CleanupPolicy.Delete, now: Long) {
    policy.retentionMs?.let { retentionMs ->
        deleteOldestSegmentsWhile { it.isExpired(now, retentionMs) }
    }
    policy.retentionBytes?.let { retentionBytes ->
        deleteOldestSegmentsWhile { sizeBytes - it.sizeBytes >= retentionBytes }
    }
}
