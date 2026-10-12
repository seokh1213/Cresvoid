class Table private constructor(
    val schema: Schema,
    private val output: Output,
    private val store: ObjectStore,
    metadataNames: MetadataNames,
) {
    private val transactions = HashMap<String, Transaction>()
    private val writtenFileNames = HashSet<String>()
    private val committer = Committer(store, metadataNames)
    private val planner = ScanPlanner(schema)

    fun execute(command: Command) {
        when (command) {
            is Command.Begin -> begin(command)
            is Command.TransactionCommand -> runInTransaction(command)
            Command.ListSnapshots -> output.snapshots(store.currentMetadata())
            is Command.ListManifests -> listManifests(command)
            is Command.ListEntries -> listEntries(command)
            Command.ListObjects -> output.objects(store.names())
            is Command.Scan -> scan(command)
        }
    }

    private fun begin(command: Command.Begin) {
        if (command.transaction in transactions) {
            return output.transactionError(command.transaction, ErrorCode.TX_EXISTS)
        }
        val transaction = Transaction(command.transaction, store.currentMetadata())
        transactions[transaction.name] = transaction
        output.begun(transaction)
    }

    private fun runInTransaction(command: Command.TransactionCommand) {
        val transaction = transactions[command.transaction]
        when {
            transaction == null -> output.transactionError(command.transaction, ErrorCode.NO_TX)
            !transaction.isActive -> output.transactionError(command.transaction, ErrorCode.TX_ENDED)
            else -> when (command) {
                is Command.Append -> append(transaction, command)
                is Command.Remove -> remove(transaction, command)
                is Command.Commit -> commit(transaction)
            }
        }
    }

    private fun append(transaction: Transaction, command: Command.Append) {
        if (command.file in writtenFileNames) {
            return output.transactionError(transaction.name, ErrorCode.FILE_EXISTS, command.file)
        }
        val partitions = command.rows.map(schema::partitionOf)
        if (partitions.any { it != partitions.first() }) {
            return output.transactionError(transaction.name, ErrorCode.MIXED_PARTITION)
        }
        val file = DataFile(command.file, partitions.first(), command.rows)
        store.write(file)
        writtenFileNames += file.name
        transaction.add(file)
        output.appended(transaction, file, schema)
    }

    private fun remove(transaction: Transaction, command: Command.Remove) {
        val liveAtStart = transaction.startSnapshot?.isLive(command.file) == true
        if (!liveAtStart || command.file in transaction.filesToRemove) {
            return output.transactionError(transaction.name, ErrorCode.NOT_LIVE, command.file)
        }
        transaction.remove(command.file)
        output.removed(transaction, command.file)
    }

    private fun commit(transaction: Transaction) {
        if (transaction.isEmpty) return output.transactionError(transaction.name, ErrorCode.EMPTY)
        val attempt = committer.attempt(transaction, transaction.baseVersion, attemptNumber = 1)
        check(attempt.committed) { "1단계는 기준 판이 현재 메타데이터 파일인 트랜잭션의 COMMIT 만 다룬다" }
        transaction.end()
        output.committed(transaction, attempt.metadata)
    }

    private fun listManifests(command: Command.ListManifests) {
        val snapshot = store.currentMetadata().snapshot(command.snapshot)
            ?: return output.error(ErrorCode.NO_SNAPSHOT, command.snapshot)
        output.manifests(snapshot, schema)
    }

    private fun listEntries(command: Command.ListEntries) {
        val listed = findListedManifest(command.manifest)
            ?: return output.error(ErrorCode.NO_MANIFEST, command.manifest)
        output.entries(listed, schema)
    }

    private fun findListedManifest(name: String): ListedManifest? =
        store.currentMetadata().snapshots.asReversed().firstNotNullOfOrNull { snapshot ->
            snapshot.manifestList.manifests.firstOrNull { it.manifest.name == name }
        }

    private fun scan(command: Command.Scan) {
        val metadata = store.currentMetadata()
        val snapshot = if (command.snapshot == null) {
            metadata.currentSnapshot ?: return output.scanned(ScanResult.NOTHING_TO_READ)
        } else {
            metadata.snapshot(command.snapshot) ?: return output.error(ErrorCode.NO_SNAPSHOT, command.snapshot)
        }
        output.scanned(planner.scan(snapshot, command.conditions))
    }

    companion object {
        fun create(schema: Schema, output: Output): Table {
            val metadataNames = MetadataNames()
            val initialMetadata = TableMetadata.initial(metadataNames.next())
            val table = Table(schema, output, ObjectStore(initialMetadata), metadataNames)
            output.created(initialMetadata)
            return table
        }
    }
}
