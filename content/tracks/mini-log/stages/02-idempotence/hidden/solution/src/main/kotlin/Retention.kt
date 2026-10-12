fun PartitionLog.applyRetention(config: TopicConfig, now: Long) {
    config.retentionMs?.let { retentionMs ->
        deleteOldestSegmentsWhile { it.isExpired(now, retentionMs) }
    }
    config.retentionBytes?.let { retentionBytes ->
        deleteOldestSegmentsWhile { sizeBytes - it.sizeBytes >= retentionBytes }
    }
}
