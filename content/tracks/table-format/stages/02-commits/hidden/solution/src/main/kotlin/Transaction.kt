class AddedFile(val file: DataFile, val explicitSequenceNumber: Long?)

class Transaction(val name: String, val baseVersion: TableMetadata) {
    private val _filesToAdd = mutableListOf<AddedFile>()
    private val _filesToRemove = linkedSetOf<String>()
    private var manifestsWritten = 0

    var isActive = true
        private set

    var newManifest: Manifest? = null

    val startSnapshot: Snapshot? get() = baseVersion.currentSnapshot
    val filesToAdd: List<AddedFile> get() = _filesToAdd
    val filesToRemove: Set<String> get() = _filesToRemove
    val writtenFileNames: List<String> get() = _filesToAdd.map { it.file.name }
    val isEmpty: Boolean get() = _filesToAdd.isEmpty() && _filesToRemove.isEmpty()

    fun append(file: DataFile) {
        _filesToAdd += AddedFile(file, explicitSequenceNumber = null)
    }

    fun remove(fileName: String) {
        _filesToRemove += fileName
    }

    fun compact(newFile: DataFile, replacedFiles: List<String>, startSequenceNumber: Long) {
        _filesToAdd += AddedFile(newFile, explicitSequenceNumber = startSequenceNumber)
        _filesToRemove += replacedFiles
    }

    fun nextManifestName(): String = "$name-m${++manifestsWritten}"

    fun end() {
        isActive = false
    }
}
