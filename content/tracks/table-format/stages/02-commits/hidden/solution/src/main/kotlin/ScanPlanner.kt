class ScannedFile(val name: String, val matchingRows: Int)

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

class ScanPlanner(private val schema: Schema) {

    fun scan(snapshot: Snapshot, conditions: List<Condition>): ScanResult {
        val filters = partitionFilters(conditions)
        val listedManifests = snapshot.manifestList.manifests
        val readManifests = listedManifests.filter { listed -> filters.none { it.excludes(listed.partitionRanges) } }
        val plannedFiles = readManifests
            .flatMap { it.manifest.liveEntries }
            .map { it.file }
            .filter { file -> conditions.none { it.excludes(file) } && filters.none { it.excludes(file.partition) } }
        val scannedFiles = plannedFiles
            .map { file -> ScannedFile(file.name, file.rows.count { row -> conditions.all { it.matches(row) } }) }
            .sortedBy { it.name }
        return ScanResult(snapshot.id, readManifests.size, listedManifests.size, scannedFiles)
    }

    private fun partitionFilters(conditions: List<Condition>): List<PartitionFilter> =
        conditions.mapNotNull { condition ->
            val field = schema.fields.indexOfFirst { it.sourceColumn == condition.column }
            if (field < 0) null else PartitionFilter(field, schema.fields[field].allowedResults(condition))
        }
}
