class AppliedDeletes(
    val fileCount: Int,
    private val deletedPositions: Set<Int>,
    private val deletesEqualRow: (Row) -> Boolean,
) {
    fun isDeleted(position: Int, row: Row): Boolean = position in deletedPositions || deletesEqualRow(row)
}

// 동등 삭제 파일은 커밋된 뒤 지워지지 않으므로 스냅숏 S 에 살아 있는 동등 삭제 파일은 순서 번호가 S 의 순서 번호 이하인 파일이다.
class DeleteIndex(private val liveEntries: LiveEntryIndex) {
    private val positionDeletesByTarget = HashMap<String, MutableList<PositionDeleteFile>>()
    private val equalitySequencesByPartition = HashMap<PartitionValue, MutableList<Long>>()
    private val equalityKeysByPartition = HashMap<PartitionValue, HashMap<List<Int>, HashMap<Row, MutableList<Long>>>>()

    fun record(file: DeleteFile, sequenceNumber: Long) {
        when (file) {
            is PositionDeleteFile -> positionDeletesByTarget.getOrPut(file.target.name) { mutableListOf() } += file
            is EqualityDeleteFile -> recordEqualityDelete(file, sequenceNumber)
        }
    }

    private fun recordEqualityDelete(file: EqualityDeleteFile, sequenceNumber: Long) {
        equalitySequencesByPartition.getOrPut(file.partition) { mutableListOf() } += sequenceNumber
        val keys = equalityKeysByPartition.getOrPut(file.partition) { HashMap() }.getOrPut(file.columns) { HashMap() }
        for (key in file.rows.distinct()) keys.getOrPut(key) { mutableListOf() } += sequenceNumber
    }

    fun positionDeletesOf(dataFileName: String): List<PositionDeleteFile> = positionDeletesByTarget[dataFileName].orEmpty()

    fun applicableTo(file: DataFile, dataSequenceNumber: Long, snapshot: Snapshot): AppliedDeletes {
        val positionDeletes = positionDeletesOf(file.name).filter { delete ->
            val live = liveEntries.find(delete.name, snapshot)
            live != null && dataSequenceNumber <= live.sequenceNumber
        }
        val laterThanData = dataSequenceNumber
        val upToSnapshot = snapshot.sequenceNumber
        val equalityCount = equalitySequencesByPartition[file.partition]?.countIn(laterThanData, upToSnapshot) ?: 0
        val keyGroups = equalityKeysByPartition[file.partition].orEmpty()
        return AppliedDeletes(
            fileCount = positionDeletes.size + equalityCount,
            deletedPositions = positionDeletes.flatMapTo(HashSet()) { it.positions },
            deletesEqualRow = { row ->
                keyGroups.any { (columns, keys) ->
                    val sequences = keys[columns.map { row[it] }]
                    sequences != null && sequences.countIn(laterThanData, upToSnapshot) > 0
                }
            },
        )
    }
}

private fun List<Long>.countIn(after: Long, upTo: Long): Int = countAtMost(upTo) - countAtMost(after)

private fun List<Long>.countAtMost(limit: Long): Int {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (this[middle] <= limit) low = middle + 1 else high = middle
    }
    return low
}
