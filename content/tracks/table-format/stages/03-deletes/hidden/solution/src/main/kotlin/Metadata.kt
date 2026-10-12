class TableMetadata(
    override val name: String,
    val snapshots: List<Snapshot>,
    val currentSnapshot: Snapshot?,
    val lastSequenceNumber: Long,
    val previousMetadata: List<String>,
) : StoredObject {
    fun snapshot(id: String): Snapshot? = snapshots.firstOrNull { it.id == id }

    companion object {
        fun initial(name: String) = TableMetadata(name, emptyList(), null, 0, emptyList())
    }
}

class Snapshot(
    val id: String,
    val sequenceNumber: Long,
    val parentId: String?,
    val manifestList: ManifestList,
)

class MetadataNames {
    private var written = 0

    fun next(): String = "meta-${++written}"
}
