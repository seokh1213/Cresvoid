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
    private val deletes: DeleteIndex,
) {

    fun attempt(transaction: Transaction, base: TableMetadata, attemptNumber: Int): CommitAttempt {
        val sequenceNumber = base.lastSequenceNumber + 1
        val parent = base.currentSnapshot
        val newDataManifest = newDataManifestOf(transaction)
        val newDeleteManifest = newDeleteManifestOf(transaction)
        val (parentData, parentDeletes) = carryParentManifests(transaction, parent, sequenceNumber)
            .partition { it.content == ManifestContent.DATA }
        val manifestList = ManifestList(
            name = "snap-${transaction.name}-$attemptNumber",
            manifests = listOfNotNull(newDataManifest?.let { ListedManifest.of(it, sequenceNumber) }) + parentData +
                listOfNotNull(newDeleteManifest?.let { ListedManifest.of(it, sequenceNumber) }) + parentDeletes,
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

    private fun newDataManifestOf(transaction: Transaction): Manifest? {
        if (transaction.dataFilesToAdd.isEmpty()) return null
        return transaction.newDataManifest
            ?: writeNewManifest(transaction, ManifestContent.DATA, transaction.dataFilesToAdd).also { transaction.newDataManifest = it }
    }

    private fun newDeleteManifestOf(transaction: Transaction): Manifest? {
        if (transaction.deleteFilesToAdd.isEmpty()) return null
        return transaction.newDeleteManifest
            ?: writeNewManifest(transaction, ManifestContent.DELETES, transaction.deleteFilesToAdd).also { transaction.newDeleteManifest = it }
    }

    private fun writeNewManifest(transaction: Transaction, content: ManifestContent, files: List<AddedFile>): Manifest {
        val entries = files.map { added ->
            ManifestEntry(EntryStatus.ADDED, added.file, transaction.name, added.explicitSequenceNumber)
        }
        return Manifest(transaction.nextManifestName(), content, entries).also(store::write)
    }

    private fun carryParentManifests(
        transaction: Transaction,
        parent: Snapshot?,
        sequenceNumber: Long,
    ): List<ListedManifest> {
        if (parent == null) return emptyList()
        val removedFiles = filesRemovedFrom(parent, transaction)
        val manifestsToRewrite = removedFiles.mapTo(HashSet()) { fileName ->
            checkNotNull(liveEntries.find(fileName, parent)) { "지울 파일 $fileName 은 부모 스냅숏에 살아 있어야 한다" }.listed.manifest
        }
        return parent.manifestList.manifests.mapNotNull { listed ->
            when {
                listed.manifest in manifestsToRewrite -> ListedManifest.of(rewrite(listed, transaction, removedFiles), sequenceNumber)
                listed.manifest.liveEntries.isNotEmpty() -> listed
                else -> null
            }
        }
    }

    private fun filesRemovedFrom(parent: Snapshot, transaction: Transaction): Set<String> {
        val positionDeletesOfRemovedFiles = transaction.filesToRemove
            .flatMap(deletes::positionDeletesOf)
            .filter { liveEntries.find(it.name, parent) != null }
            .map { it.name }
        return transaction.filesToRemove + positionDeletesOfRemovedFiles
    }

    private fun rewrite(listed: ListedManifest, transaction: Transaction, removedFiles: Set<String>): Manifest {
        val entries = listed.manifest.liveEntries.map { entry ->
            val sequenceNumber = listed.sequenceNumberOf(entry)
            if (entry.file.name in removedFiles) {
                ManifestEntry(EntryStatus.DELETED, entry.file, transaction.name, sequenceNumber)
            } else {
                ManifestEntry(EntryStatus.EXISTING, entry.file, entry.snapshotId, sequenceNumber)
            }
        }
        return Manifest(transaction.nextManifestName(), listed.manifest.content, entries).also(store::write)
    }
}
