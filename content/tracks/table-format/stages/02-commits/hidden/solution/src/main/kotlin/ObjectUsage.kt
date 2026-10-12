class ObjectUsage {
    private val snapshotsListing = HashMap<Manifest, Int>()
    private val manifestsReferencing = HashMap<String, Int>()

    fun retain(snapshot: Snapshot) {
        for (listed in snapshot.manifestList.manifests) {
            if (snapshotsListing.increment(listed.manifest) > 1) continue
            for (entry in listed.manifest.liveEntries) manifestsReferencing.increment(entry.file.name)
        }
    }

    fun release(snapshot: Snapshot): List<String> {
        val unused = mutableListOf(snapshot.manifestList.name)
        for (listed in snapshot.manifestList.manifests) {
            if (snapshotsListing.decrement(listed.manifest) > 0) continue
            unused += listed.manifest.name
            for (entry in listed.manifest.liveEntries) {
                if (manifestsReferencing.decrement(entry.file.name) == 0) unused += entry.file.name
            }
        }
        return unused
    }

    fun unusedObjects(
        stored: Collection<StoredObject>,
        metadata: TableMetadata,
        activeTransactions: List<Transaction>,
    ): List<StoredObject> {
        val usedMetadata = (metadata.previousMetadata + metadata.name).toHashSet()
        val usedLists = metadata.snapshots.mapTo(HashSet()) { it.manifestList.name }
        val activeFiles = activeTransactions.flatMapTo(HashSet()) { it.writtenFileNames }
        return stored.filter { candidate ->
            when (candidate) {
                is TableMetadata -> candidate.name !in usedMetadata
                is ManifestList -> candidate.name !in usedLists
                is Manifest -> candidate !in snapshotsListing
                is DataFile -> candidate.name !in manifestsReferencing && candidate.name !in activeFiles
            }
        }
    }
}

private fun <K> HashMap<K, Int>.increment(key: K): Int = merge(key, 1, Int::plus)!!

private fun <K> HashMap<K, Int>.decrement(key: K): Int {
    val count = getValue(key) - 1
    if (count == 0) remove(key) else this[key] = count
    return count
}
