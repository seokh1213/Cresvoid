const val EMPTY_LINE = "(empty)"
const val NONE_LINE = "(none)"
private const val ABSENT = "-"
private const val TOMBSTONE_VALUE = "(null)"

fun recordLocationLine(location: RecordLocation): String = "partition=${location.partition} offset=${location.offset}"

fun recordLine(record: Record): String =
    "${record.offset} ${record.message.key ?: ABSENT} ${record.message.value ?: TOMBSTONE_VALUE}"

fun offsetsLine(log: PartitionLog): String = "start=${log.logStartOffset} end=${log.logEndOffset}"

fun segmentLine(segment: Segment, isActive: Boolean): String {
    val line = "base=${segment.baseOffset} records=${segment.recordCount} bytes=${segment.sizeBytes} " +
        "max_time=${segment.maxTimestamp ?: ABSENT}"
    return if (isActive) "$line active" else line
}

fun producerIdLine(session: ProducerSession): String = "pid=${session.producerId}"

fun batchLine(batch: ProducerBatch): String =
    "pid=${batch.producerId} seq=${batch.sequences.first}-${batch.sequences.last}"

fun deliveryLine(delivery: Delivery): String {
    val outcome = when (delivery) {
        is Delivery.Appended -> "appended"
        is Delivery.Duplicate -> "duplicate"
    }
    return "$outcome ${delivery.offsets.first}-${delivery.offsets.last}"
}

fun producerStateLine(state: ProducerState): String {
    val cached = state.cachedBatches.joinToString(",") { "${it.sequences.first}-${it.sequences.last}" }
    return "pid=${state.producerId} last_seq=${state.lastSequence} cached=$cached"
}

fun polledLine(polled: PolledRecord): String =
    "${polled.partition.topic} ${polled.partition.partition} ${recordLine(polled.record)}"

fun expirationLine(expiration: Expiration): String =
    "${expiration.deadline} ${expiration.group} ${expiration.consumer} ${expiration.kind.label}"

fun assignmentLines(group: ConsumerGroup): List<String> {
    val memberLines = group.members.map { member ->
        val status = if (member.state is ConsumerState.Crashed) "crashed" else "alive"
        val assigned = group.assignmentOf(member.name)
        val partitions = if (assigned.isEmpty()) {
            ABSENT
        } else {
            assigned.joinToString(",") { "${it.topic}-${it.partition}" }
        }
        "${member.name} $status $partitions"
    }
    return listOf("generation=${group.generation}") + memberLines
}

fun committedLine(committed: CommittedOffset): String =
    "${committed.partition.topic} ${committed.partition.partition} " +
        "committed=${committed.offset ?: ABSENT} end=${committed.logEndOffset}"

fun dirtyLine(stats: DirtyStats): String =
    "first_dirty=${stats.firstDirtyOffset} clean_bytes=${stats.cleanBytes} dirty_bytes=${stats.dirtyBytes}"
