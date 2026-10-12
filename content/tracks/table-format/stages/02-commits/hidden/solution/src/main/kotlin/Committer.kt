class CommitAttempt(
    val metadata: TableMetadata,
    val snapshot: Snapshot,
    val changedManifests: List<ListedManifest>,
    val committed: Boolean,
)

class Committer(
    private val store: ObjectStore,
    private val metadataNames: MetadataNames,
    private val liveEntries: LiveEntryIndex,
) {

    fun attempt(transaction: Transaction, base: TableMetadata, attemptNumber: Int): CommitAttempt {
        val sequenceNumber = base.lastSequenceNumber + 1
        val parent = base.currentSnapshot
        val newManifest = newManifestOf(transaction)
        val parentManifests = carryParentManifests(transaction, parent, sequenceNumber)
        val manifestList = ManifestList(
            name = "snap-${transaction.name}-$attemptNumber",
            manifests = listOfNotNull(newManifest?.let { ListedManifest.of(it, sequenceNumber) }) + parentManifests,
        )
        store.write(manifestList)
        val snapshot = Snapshot(transaction.name, sequenceNumber, parent?.id, manifestList)
        val metadata = TableMetadata(
            name = metadataNames.next(),
            snapshots = base.snapshots + snapshot,
            currentSnapshot = snapshot,
            lastSequenceNumber = sequenceNumber,
            previousMetadata = base.previousMetadata + base.name,
        )
        store.write(metadata)
        return CommitAttempt(
            metadata = metadata,
            snapshot = snapshot,
            changedManifests = manifestList.manifests.filter { it.sequenceNumber == sequenceNumber },
            committed = store.swap(base.name, metadata.name),
        )
    }

    private fun newManifestOf(transaction: Transaction): Manifest? {
        if (transaction.filesToAdd.isEmpty()) return null
        transaction.newManifest?.let { return it }
        val entries = transaction.filesToAdd.map { added ->
            ManifestEntry(EntryStatus.ADDED, added.file, transaction.name, added.explicitSequenceNumber)
        }
        val manifest = Manifest(transaction.nextManifestName(), ManifestContent.DATA, entries)
        store.write(manifest)
        transaction.newManifest = manifest
        return manifest
    }

    private fun carryParentManifests(
        transaction: Transaction,
        parent: Snapshot?,
        sequenceNumber: Long,
    ): List<ListedManifest> {
        if (parent == null) return emptyList()
        val manifestsToRewrite = transaction.filesToRemove.mapTo(HashSet()) { fileName ->
            checkNotNull(liveEntries.find(fileName, parent)) { "지울 파일 $fileName 은 부모 스냅숏에 살아 있어야 한다" }.listed.manifest
        }
        return parent.manifestList.manifests.mapNotNull { listed ->
            when {
                listed.manifest in manifestsToRewrite -> ListedManifest.of(rewrite(listed, transaction), sequenceNumber)
                listed.manifest.liveEntries.isNotEmpty() -> listed
                else -> null
            }
        }
    }

    private fun rewrite(listed: ListedManifest, transaction: Transaction): Manifest {
        val entries = listed.manifest.liveEntries.map { entry ->
            val sequenceNumber = listed.sequenceNumberOf(entry)
            if (entry.file.name in transaction.filesToRemove) {
                ManifestEntry(EntryStatus.DELETED, entry.file, transaction.name, sequenceNumber)
            } else {
                ManifestEntry(EntryStatus.EXISTING, entry.file, entry.snapshotId, sequenceNumber)
            }
        }
        return Manifest(transaction.nextManifestName(), listed.manifest.content, entries).also(store::write)
    }
}
