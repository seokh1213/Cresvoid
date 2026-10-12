class AddedFile(val file: ContentFile, val explicitSequenceNumber: Long?)

class Transaction(val name: String, val baseVersion: TableMetadata) {
    private val _filesToAdd = mutableListOf<AddedFile>()
    private val _filesToRemove = linkedSetOf<String>()
    private val _replacedFiles = linkedSetOf<String>()
    private val _positionDeleteTargets = linkedSetOf<String>()
    private var manifestsWritten = 0

    var isActive = true
        private set

    var newDataManifest: Manifest? = null
    var newDeleteManifest: Manifest? = null

    val startSnapshot: Snapshot? get() = baseVersion.currentSnapshot
    val dataFilesToAdd: List<AddedFile> get() = _filesToAdd.filter { it.file is DataFile }
    val deleteFilesToAdd: List<AddedFile> get() = _filesToAdd.filter { it.file is DeleteFile }
    val filesToRemove: Set<String> get() = _filesToRemove
    val replacedFiles: Set<String> get() = _replacedFiles
    val positionDeleteTargets: Set<String> get() = _positionDeleteTargets
    val writtenFileNames: List<String> get() = _filesToAdd.map { it.file.name }
    val isEmpty: Boolean get() = _filesToAdd.isEmpty() && _filesToRemove.isEmpty()

    fun writtenDataFile(fileName: String): DataFile? =
        _filesToAdd.firstNotNullOfOrNull { added -> (added.file as? DataFile)?.takeIf { it.name == fileName } }

    fun append(file: DataFile) {
        _filesToAdd += AddedFile(file, explicitSequenceNumber = null)
    }

    fun remove(fileName: String) {
        _filesToRemove += fileName
    }

    fun compact(newFile: DataFile, replacedFiles: List<String>, startSequenceNumber: Long) {
        _filesToAdd += AddedFile(newFile, explicitSequenceNumber = startSequenceNumber)
        _filesToRemove += replacedFiles
        _replacedFiles += replacedFiles
    }

    fun deletePositions(file: PositionDeleteFile) {
        _filesToAdd += AddedFile(file, explicitSequenceNumber = null)
        _positionDeleteTargets += file.target.name
    }

    fun deleteEqualRows(file: EqualityDeleteFile) {
        _filesToAdd += AddedFile(file, explicitSequenceNumber = null)
    }

    fun nextManifestName(): String = "$name-m${++manifestsWritten}"

    fun end() {
        isActive = false
    }
}
