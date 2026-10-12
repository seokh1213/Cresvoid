class CommitAttempt(val metadata: TableMetadata, val committed: Boolean)

class Committer(private val store: ObjectStore, private val metadataNames: MetadataNames) {

    fun attempt(transaction: Transaction, base: TableMetadata, attemptNumber: Int): CommitAttempt {
        val sequenceNumber = base.lastSequenceNumber + 1
        val parent = base.currentSnapshot
        val newManifest = writeNewManifest(transaction)
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
        return CommitAttempt(metadata, committed = store.swap(base.name, metadata.name))
    }

    private fun writeNewManifest(transaction: Transaction): Manifest? {
        if (transaction.filesToAdd.isEmpty()) return null
        val entries = transaction.filesToAdd.map { file ->
            ManifestEntry(EntryStatus.ADDED, file, transaction.name, explicitSequenceNumber = null)
        }
        return Manifest(transaction.nextManifestName(), ManifestContent.DATA, entries).also(store::write)
    }

    private fun carryParentManifests(
        transaction: Transaction,
        parent: Snapshot?,
        sequenceNumber: Long,
    ): List<ListedManifest> = parent?.manifestList?.manifests.orEmpty().mapNotNull { listed ->
        when {
            transaction.filesToRemove.any { listed.manifest.liveEntry(it) != null } ->
                ListedManifest.of(rewrite(listed, transaction), sequenceNumber)
            listed.manifest.liveEntries.isNotEmpty() -> listed
            else -> null
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
