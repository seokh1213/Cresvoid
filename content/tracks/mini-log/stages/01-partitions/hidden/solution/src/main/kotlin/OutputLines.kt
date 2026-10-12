const val EMPTY_LINE = "(empty)"
private const val ABSENT = "-"

fun recordLocationLine(location: RecordLocation): String = "partition=${location.partition} offset=${location.offset}"

fun recordLine(record: Record): String = "${record.offset} ${record.message.key ?: ABSENT} ${record.message.value}"

fun offsetsLine(log: PartitionLog): String = "start=${log.logStartOffset} end=${log.logEndOffset}"

fun segmentLine(segment: Segment, isActive: Boolean): String {
    val line = "base=${segment.baseOffset} records=${segment.recordCount} bytes=${segment.sizeBytes} " +
        "max_time=${segment.maxTimestamp ?: ABSENT}"
    return if (isActive) "$line active" else line
}
