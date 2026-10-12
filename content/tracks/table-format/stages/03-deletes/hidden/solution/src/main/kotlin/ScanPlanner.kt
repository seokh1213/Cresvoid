class ScannedFile(val name: String, val matchingRows: Int, val deleteFiles: Int)

class ScanResult(
    val snapshotId: String?,
    val manifestsRead: Int,
    val manifestsListed: Int,
    val files: List<ScannedFile>,
) {
    val matchingRows: Int get() = files.sumOf { it.matchingRows }

    companion object {
        val NOTHING_TO_READ = ScanResult(null, 0, 0, emptyList())
    }
}

private class PartitionFilter(val field: Int, val allowed: IntRange) {
    fun excludes(ranges: List<IntRange>): Boolean {
        val range = ranges[field]
        return maxOf(range.first, allowed.first) > minOf(range.last, allowed.last)
    }

    fun excludes(partition: PartitionValue): Boolean = partition.results[field] !in allowed
}

private class PlannedFile(val file: DataFile, val sequenceNumber: Long)

class ScanPlanner(private val schema: Schema, private val deletes: DeleteIndex) {

    fun scan(snapshot: Snapshot, conditions: List<Condition>): ScanResult {
        val filters = partitionFilters(conditions)
        val listedManifests = snapshot.manifestList.manifests
        val readManifests = listedManifests.filter { listed -> filters.none { it.excludes(listed.partitionRanges) } }
        val plannedFiles = readManifests
            .filter { it.content == ManifestContent.DATA }
            .flatMap { listed -> plannedFilesOf(listed, filters, conditions) }
        val scannedFiles = plannedFiles
            .map { scanFile(it, snapshot, conditions) }
            .sortedBy { it.name }
        return ScanResult(snapshot.id, readManifests.size, listedManifests.size, scannedFiles)
    }

    private fun plannedFilesOf(
        listed: ListedManifest,
        filters: List<PartitionFilter>,
        conditions: List<Condition>,
    ): List<PlannedFile> = listed.manifest.liveEntries.mapNotNull { entry ->
        val file = entry.file as DataFile
        val excluded = conditions.any { it.excludes(file) } || filters.any { it.excludes(file.partition) }
        if (excluded) null else PlannedFile(file, listed.sequenceNumberOf(entry))
    }

    private fun scanFile(planned: PlannedFile, snapshot: Snapshot, conditions: List<Condition>): ScannedFile {
        val applied = deletes.applicableTo(planned.file, planned.sequenceNumber, snapshot)
        val matchingRows = planned.file.rows.withIndex().count { (position, row) ->
            conditions.all { it.matches(row) } && !applied.isDeleted(position, row)
        }
        return ScannedFile(planned.file.name, matchingRows, applied.fileCount)
    }

    private fun partitionFilters(conditions: List<Condition>): List<PartitionFilter> =
        conditions.mapNotNull { condition ->
            val field = schema.fields.indexOfFirst { it.sourceColumn == condition.column }
            if (field < 0) null else PartitionFilter(field, schema.fields[field].allowedResults(condition))
        }
}
