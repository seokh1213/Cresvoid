class Table private constructor(
    val schema: Schema,
    private val output: Output,
    private val store: ObjectStore,
    private val metadataNames: MetadataNames,
) {
    private val transactions = HashMap<String, Transaction>()
    private val writtenFileNames = HashSet<String>()
    private val liveEntries = LiveEntryIndex()
    private val deletes = DeleteIndex(liveEntries)
    private val usage = ObjectUsage()
    private val committer = Committer(store, metadataNames, liveEntries, deletes)
    private val planner = ScanPlanner(schema, deletes)

    fun execute(command: Command) {
        when (command) {
            is Command.Begin -> begin(command)
            is Command.TransactionCommand -> runInTransaction(command)
            is Command.Expire -> expire(command)
            Command.RemoveOrphans -> removeOrphans()
            Command.ListSnapshots -> output.snapshots(store.currentMetadata())
            is Command.ListManifests -> listManifests(command)
            is Command.ListEntries -> listEntries(command)
            Command.ListObjects -> output.objects(store.names().sorted())
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
                is Command.Compact -> compact(transaction, command)
                is Command.PositionDelete -> deletePositions(transaction, command)
                is Command.EqualityDelete -> deleteEqualRows(transaction, command)
                is Command.Commit -> commit(transaction)
                is Command.Abort -> abort(transaction)
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
        writeFile(file)
        transaction.append(file)
        output.appended(transaction, file, schema)
    }

    private fun remove(transaction: Transaction, command: Command.Remove) {
        if (removableAtStart(transaction, command.file) == null) {
            return output.transactionError(transaction.name, ErrorCode.NOT_LIVE, command.file)
        }
        transaction.remove(command.file)
        output.removed(transaction, command.file)
    }

    private fun compact(transaction: Transaction, command: Command.Compact) {
        if (command.newFile in writtenFileNames) {
            return output.transactionError(transaction.name, ErrorCode.FILE_EXISTS, command.newFile)
        }
        val replacedEntries = command.replacedFiles.map { fileName ->
            removableAtStart(transaction, fileName)
                ?: return output.transactionError(transaction.name, ErrorCode.NOT_LIVE, fileName)
        }
        val partition = replacedEntries.first().file.partition
        if (replacedEntries.any { it.file.partition != partition }) {
            return output.transactionError(transaction.name, ErrorCode.MIXED_PARTITION)
        }
        val startSnapshot = checkNotNull(transaction.startSnapshot)
        val survivingRows = replacedEntries.flatMap { rowsNotDeleted(it, startSnapshot) }
        if (survivingRows.isEmpty()) return output.transactionError(transaction.name, ErrorCode.NO_ROWS)
        val file = DataFile(command.newFile, partition, survivingRows)
        writeFile(file)
        transaction.compact(file, command.replacedFiles, startSnapshot.sequenceNumber)
        output.compacted(transaction, file, schema)
    }

    private fun rowsNotDeleted(live: LiveEntry, snapshot: Snapshot): List<Row> {
        val file = live.file as DataFile
        val applied = deletes.applicableTo(file, live.sequenceNumber, snapshot)
        return file.rows.filterIndexed { position, row -> !applied.isDeleted(position, row) }
    }

    private fun deletePositions(transaction: Transaction, command: Command.PositionDelete) {
        if (command.deleteFile in writtenFileNames) {
            return output.transactionError(transaction.name, ErrorCode.FILE_EXISTS, command.deleteFile)
        }
        val target = positionDeleteTarget(transaction, command.dataFile)
            ?: return output.transactionError(transaction.name, ErrorCode.NOT_LIVE, command.dataFile)
        val badPosition = command.positions.firstOrNull { it >= target.rowCount }
        if (badPosition != null) {
            return output.transactionError(transaction.name, ErrorCode.BAD_POSITION, badPosition.toString())
        }
        val file = PositionDeleteFile(command.deleteFile, target, command.positions)
        writeFile(file)
        transaction.deletePositions(file)
        output.positionsDeleted(transaction, file, schema)
    }

    private fun deleteEqualRows(transaction: Transaction, command: Command.EqualityDelete) {
        if (command.deleteFile in writtenFileNames) {
            return output.transactionError(transaction.name, ErrorCode.FILE_EXISTS, command.deleteFile)
        }
        val partitions = command.rows.map { schema.partitionOf(command.columns, it) }
        if (partitions.any { it != partitions.first() }) {
            return output.transactionError(transaction.name, ErrorCode.MIXED_PARTITION)
        }
        val file = EqualityDeleteFile(command.deleteFile, command.columnList, command.columns, partitions.first(), command.rows)
        writeFile(file)
        transaction.deleteEqualRows(file)
        output.equalRowsDeleted(transaction, file, schema)
    }

    private fun liveDataFileAtStart(transaction: Transaction, fileName: String): LiveEntry? {
        val startSnapshot = transaction.startSnapshot ?: return null
        return liveEntries.find(fileName, startSnapshot)?.takeIf { it.file is DataFile }
    }

    private fun removableAtStart(transaction: Transaction, fileName: String): LiveEntry? {
        if (fileName in transaction.filesToRemove || fileName in transaction.positionDeleteTargets) return null
        return liveDataFileAtStart(transaction, fileName)
    }

    private fun positionDeleteTarget(transaction: Transaction, fileName: String): DataFile? {
        transaction.writtenDataFile(fileName)?.let { return it }
        if (fileName in transaction.filesToRemove) return null
        return liveDataFileAtStart(transaction, fileName)?.file as DataFile?
    }

    private fun writeFile(file: ContentFile) {
        store.write(file)
        writtenFileNames += file.name
    }

    private fun commit(transaction: Transaction) {
        if (transaction.isEmpty) return output.transactionError(transaction.name, ErrorCode.EMPTY)
        val firstAttempt = committer.attempt(transaction, transaction.baseVersion, attemptNumber = 1)
        if (firstAttempt.committed) return finishCommit(transaction, firstAttempt)
        output.retried(transaction, firstAttempt.metadata)
        val current = store.currentMetadata()
        val conflictingFile = conflictingFiles(transaction, checkNotNull(current.currentSnapshot)).minOrNull()
        if (conflictingFile != null) {
            transaction.end()
            return output.conflicted(transaction, conflictingFile)
        }
        val secondAttempt = committer.attempt(transaction, current, attemptNumber = 2)
        check(secondAttempt.committed) { "두 번째 시도의 기준 판은 현재 메타데이터 파일이다" }
        finishCommit(transaction, secondAttempt)
    }

    private fun conflictingFiles(transaction: Transaction, snapshot: Snapshot): List<String> {
        val startSequenceNumber = transaction.startSnapshot?.sequenceNumber ?: 0
        val removedElsewhere = transaction.filesToRemove.filter { liveEntries.find(it, snapshot) == null }
        val lostTargets = (transaction.positionDeleteTargets - transaction.writtenFileNames.toSet())
            .filter { liveEntries.find(it, snapshot) == null }
        val newlyDeletedReplacements = transaction.replacedFiles.filter { replaced ->
            deletes.positionDeletesOf(replaced).any { delete ->
                val live = liveEntries.find(delete.name, snapshot)
                live != null && live.sequenceNumber > startSequenceNumber
            }
        }
        return removedElsewhere + lostTargets + newlyDeletedReplacements
    }

    private fun finishCommit(transaction: Transaction, attempt: CommitAttempt) {
        liveEntries.record(attempt.snapshot, attempt.changedManifests)
        for (added in transaction.deleteFilesToAdd) {
            deletes.record(added.file as DeleteFile, attempt.snapshot.sequenceNumber)
        }
        usage.retain(attempt.snapshot)
        transaction.end()
        output.committed(transaction, attempt.metadata)
    }

    private fun abort(transaction: Transaction) {
        transaction.end()
        output.aborted(transaction)
    }

    private fun expire(command: Command.Expire) {
        val current = store.currentMetadata()
        val startSnapshotIds = activeTransactions().mapNotNull { it.startSnapshot?.id }.toSet()
        for (snapshotId in command.snapshots) {
            val snapshot = current.snapshot(snapshotId) ?: return output.error(ErrorCode.NO_SNAPSHOT, snapshotId)
            if (snapshot == current.currentSnapshot || snapshotId in startSnapshotIds) {
                return output.error(ErrorCode.IN_USE, snapshotId)
            }
        }
        val expiring = command.snapshots.toSet()
        val next = TableMetadata(
            name = metadataNames.next(),
            snapshots = current.snapshots.filter { it.id !in expiring },
            currentSnapshot = current.currentSnapshot,
            lastSequenceNumber = current.lastSequenceNumber,
            previousMetadata = current.previousMetadata + current.name,
        )
        store.write(next)
        check(store.swap(current.name, next.name)) { "EXPIRE 의 기준 판은 현재 메타데이터 파일이다" }
        val unused = current.snapshots.filter { it.id in expiring }.flatMap(usage::release)
        output.expired(next, store.deleteAll(unused))
    }

    private fun removeOrphans() {
        val orphans = usage.unusedObjects(store.objects(), store.currentMetadata(), activeTransactions())
        output.orphansRemoved(store.deleteAll(orphans.map { it.name }))
    }

    private fun activeTransactions(): List<Transaction> = transactions.values.filter { it.isActive }

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
