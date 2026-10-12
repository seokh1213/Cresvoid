class Transaction(val name: String, val baseVersion: TableMetadata) {
    private val _filesToAdd = mutableListOf<DataFile>()
    private val _filesToRemove = linkedSetOf<String>()
    private var manifestsWritten = 0

    var isActive = true
        private set

    val startSnapshot: Snapshot? get() = baseVersion.currentSnapshot
    val filesToAdd: List<DataFile> get() = _filesToAdd
    val filesToRemove: Set<String> get() = _filesToRemove
    val isEmpty: Boolean get() = _filesToAdd.isEmpty() && _filesToRemove.isEmpty()

    fun add(file: DataFile) {
        _filesToAdd += file
    }

    fun remove(fileName: String) {
        _filesToRemove += fileName
    }

    fun nextManifestName(): String = "$name-m${++manifestsWritten}"

    fun end() {
        isActive = false
    }
}
