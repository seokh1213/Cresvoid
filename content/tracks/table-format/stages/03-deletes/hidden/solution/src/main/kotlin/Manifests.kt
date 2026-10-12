enum class EntryStatus {
    ADDED,
    EXISTING,
    DELETED,
    ;

    val isLive: Boolean get() = this != DELETED
}

enum class ManifestContent(val label: String) {
    DATA("data"),
    DELETES("deletes"),
}

class ManifestEntry(
    val status: EntryStatus,
    val file: ContentFile,
    val snapshotId: String,
    val explicitSequenceNumber: Long?,
)

class Manifest(
    override val name: String,
    val content: ManifestContent,
    val entries: List<ManifestEntry>,
) : StoredObject {
    val liveEntries: List<ManifestEntry> by lazy { entries.filter { it.status.isLive } }
}

class ListedManifest(
    val manifest: Manifest,
    val sequenceNumber: Long,
    val addedFiles: Int,
    val existingFiles: Int,
    val deletedFiles: Int,
    val partitionRanges: List<IntRange>,
) {
    val content: ManifestContent get() = manifest.content

    fun sequenceNumberOf(entry: ManifestEntry): Long = entry.explicitSequenceNumber ?: sequenceNumber

    companion object {
        fun of(manifest: Manifest, sequenceNumber: Long) = ListedManifest(
            manifest = manifest,
            sequenceNumber = sequenceNumber,
            addedFiles = manifest.entries.count { it.status == EntryStatus.ADDED },
            existingFiles = manifest.entries.count { it.status == EntryStatus.EXISTING },
            deletedFiles = manifest.entries.count { it.status == EntryStatus.DELETED },
            partitionRanges = partitionRanges(manifest.entries.map { it.file.partition }),
        )
    }
}

private fun partitionRanges(partitions: List<PartitionValue>): List<IntRange> =
    partitions.first().results.indices.map { field ->
        partitions.minOf { it.results[field] }..partitions.maxOf { it.results[field] }
    }

class ManifestList(override val name: String, val manifests: List<ListedManifest>) : StoredObject
