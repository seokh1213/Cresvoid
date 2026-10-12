class LiveEntry(val entry: ManifestEntry, val listed: ListedManifest) {
    val file: ContentFile get() = entry.file
    val sequenceNumber: Long get() = listed.sequenceNumberOf(entry)
}

private class Placement(val sequenceNumber: Long, val liveEntry: LiveEntry?)

// 커밋된 스냅숏은 한 줄로 이어지므로 스냅숏 S 에서 파일의 항목은 S 의 순서 번호 이하에서 마지막으로 놓인 자리다.
class LiveEntryIndex {
    private val placementsByFile = HashMap<String, MutableList<Placement>>()

    fun record(snapshot: Snapshot, changedManifests: List<ListedManifest>) {
        for (listed in changedManifests) {
            for (entry in listed.manifest.entries) {
                val liveEntry = if (entry.status.isLive) LiveEntry(entry, listed) else null
                placementsByFile.getOrPut(entry.file.name) { mutableListOf() } += Placement(snapshot.sequenceNumber, liveEntry)
            }
        }
    }

    fun find(fileName: String, snapshot: Snapshot): LiveEntry? =
        placementsByFile[fileName]?.lastOrNull { it.sequenceNumber <= snapshot.sequenceNumber }?.liveEntry
}
