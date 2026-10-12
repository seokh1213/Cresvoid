import java.io.Writer

private const val ABSENT = "-"

enum class ErrorCode {
    UNKNOWN_COMMAND,
    NO_TABLE,
    TABLE_EXISTS,
    BAD_ARGUMENT,
    TX_EXISTS,
    NO_TX,
    TX_ENDED,
    FILE_EXISTS,
    MIXED_PARTITION,
    NOT_LIVE,
    EMPTY,
    NO_SNAPSHOT,
    NO_MANIFEST,
    IN_USE,
    BAD_POSITION,
    NO_ROWS,
}

class Output(private val writer: Writer) {
    fun created(metadata: TableMetadata) = line("CREATE ${metadata.name}")

    fun begun(transaction: Transaction) {
        val startSnapshot = transaction.startSnapshot?.id ?: ABSENT
        line("${transaction.name} BEGIN $startSnapshot ${transaction.baseVersion.name}")
    }

    fun appended(transaction: Transaction, file: DataFile, schema: Schema) =
        line("${transaction.name} APPEND ${file.name} ${schema.format(file.partition)} rows=${file.rowCount}")

    fun removed(transaction: Transaction, fileName: String) = line("${transaction.name} REMOVE $fileName")

    fun compacted(transaction: Transaction, file: DataFile, schema: Schema) =
        line("${transaction.name} COMPACT ${file.name} ${schema.format(file.partition)} rows=${file.rowCount}")

    fun positionsDeleted(transaction: Transaction, file: PositionDeleteFile, schema: Schema) =
        line("${transaction.name} POSDEL ${file.name} ${schema.format(file.partition)} rows=${file.rowCount}")

    fun equalRowsDeleted(transaction: Transaction, file: EqualityDeleteFile, schema: Schema) =
        line("${transaction.name} EQDEL ${file.name} ${schema.format(file.partition)} rows=${file.rowCount}")

    fun committed(transaction: Transaction, metadata: TableMetadata) =
        line("${transaction.name} COMMIT seq=${metadata.lastSequenceNumber} ${metadata.name}")

    fun retried(transaction: Transaction, metadata: TableMetadata) = line("${transaction.name} RETRY ${metadata.name}")

    fun conflicted(transaction: Transaction, fileName: String) = line("${transaction.name} CONFLICT $fileName")

    fun aborted(transaction: Transaction) = line("${transaction.name} ABORT")

    fun expired(metadata: TableMetadata, deleted: List<StoredObject>) {
        val lists = deleted.count { it is ManifestList }
        val manifests = deleted.count { it is Manifest }
        val files = deleted.count { it is ContentFile }
        line("EXPIRE ${metadata.name} lists=$lists manifests=$manifests files=$files")
    }

    fun orphansRemoved(deleted: List<StoredObject>) {
        val metadata = deleted.count { it is TableMetadata }
        val lists = deleted.count { it is ManifestList }
        val manifests = deleted.count { it is Manifest }
        val files = deleted.count { it is ContentFile }
        line("ORPHANS metadata=$metadata lists=$lists manifests=$manifests files=$files")
    }

    fun snapshots(metadata: TableMetadata) {
        line("SNAPSHOTS ${metadata.name} ${metadata.snapshots.size}")
        for (snapshot in metadata.snapshots) {
            val parent = snapshot.parentId ?: ABSENT
            line("${snapshot.id} seq=${snapshot.sequenceNumber} parent=$parent list=${snapshot.manifestList.name}")
        }
    }

    fun manifests(snapshot: Snapshot, schema: Schema) {
        val manifests = snapshot.manifestList.manifests
        line("MANIFESTS ${snapshot.id} ${manifests.size}")
        for (listed in manifests) {
            val counts = "added=${listed.addedFiles} existing=${listed.existingFiles} deleted=${listed.deletedFiles}"
            val ranges = schema.fields.indices.joinToString("") { " " + schema.formatRange(it, listed.partitionRanges[it]) }
            line("${listed.manifest.name} ${listed.content.label} seq=${listed.sequenceNumber} $counts$ranges")
        }
    }

    fun entries(listed: ListedManifest, schema: Schema) {
        val entries = listed.manifest.entries
        line("ENTRIES ${listed.manifest.name} ${entries.size}")
        for (entry in entries) {
            val file = entry.file
            val sequenceNumber = listed.sequenceNumberOf(entry)
            val target = when (file) {
                is DataFile -> ""
                is PositionDeleteFile -> " position ${file.target.name}"
                is EqualityDeleteFile -> " equality ${file.columnList}"
            }
            line("${entry.status} ${file.name} snapshot=${entry.snapshotId} seq=$sequenceNumber ${schema.format(file.partition)} rows=${file.rowCount}$target")
        }
    }

    fun objects(names: List<String>) {
        line("LS ${names.size}")
        names.forEach(::line)
    }

    fun scanned(result: ScanResult) {
        val manifests = "manifests=${result.manifestsRead}/${result.manifestsListed}"
        line("SCAN ${result.snapshotId ?: ABSENT} $manifests files=${result.files.size} rows=${result.matchingRows}")
        for (file in result.files) {
            val deletes = if (file.deleteFiles > 0) " deletes=${file.deleteFiles}" else ""
            line("${file.name} rows=${file.matchingRows}$deletes")
        }
    }

    fun error(code: ErrorCode, subject: String? = null) =
        line(listOfNotNull("ERR", code.name, subject).joinToString(" "))

    fun transactionError(transaction: String, code: ErrorCode, subject: String? = null) =
        line(listOfNotNull(transaction, "ERR", code.name, subject).joinToString(" "))

    fun flush() = writer.flush()

    private fun line(text: String) {
        writer.write(text)
        writer.write("\n")
    }
}
