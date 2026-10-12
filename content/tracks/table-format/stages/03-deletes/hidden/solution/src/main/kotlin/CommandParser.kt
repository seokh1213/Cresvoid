private val NAME = Regex("[a-z][a-z0-9]{0,15}")
private val DAY_FIELD = Regex("day\\(([a-z][a-z0-9]{0,15})\\)")
private val BUCKET_FIELD = Regex("bucket\\[([1-9][0-9]{0,2})]\\(([a-z][a-z0-9]{0,15})\\)")
private val POSITION = Regex("0|[1-9][0-9]{0,8}")
private val COLUMN_COUNT = 1..8
private const val PARTITION = "PARTITION"
private const val AT = "AT"
private const val WHERE = "WHERE"
private const val AND = "AND"

val COMMAND_PARSERS: Map<String, (List<String>, Schema) -> Command?> = mapOf(
    "BEGIN" to { arguments, _ -> singleName(arguments)?.let(Command::Begin) },
    "APPEND" to ::parseAppend,
    "REMOVE" to { arguments, _ -> parseRemove(arguments) },
    "COMPACT" to { arguments, _ -> parseCompact(arguments) },
    "POSDEL" to { arguments, _ -> parsePositionDelete(arguments) },
    "EQDEL" to ::parseEqualityDelete,
    "COMMIT" to { arguments, _ -> singleName(arguments)?.let(Command::Commit) },
    "ABORT" to { arguments, _ -> singleName(arguments)?.let(Command::Abort) },
    "EXPIRE" to { arguments, _ -> parseExpire(arguments) },
    "ORPHANS" to { arguments, _ -> Command.RemoveOrphans.takeIf { arguments.isEmpty() } },
    "SNAPSHOTS" to { arguments, _ -> Command.ListSnapshots.takeIf { arguments.isEmpty() } },
    "MANIFESTS" to { arguments, _ -> singleName(arguments)?.let(Command::ListManifests) },
    "ENTRIES" to { arguments, _ -> arguments.singleOrNull()?.let(Command::ListEntries) },
    "LS" to { arguments, _ -> Command.ListObjects.takeIf { arguments.isEmpty() } },
    "SCAN" to ::parseScan,
)

fun parseSchema(arguments: List<String>): Schema? {
    val partitionAt = arguments.indexOf(PARTITION)
    val columnDefinitions = if (partitionAt < 0) arguments else arguments.subList(0, partitionAt)
    val fieldDefinitions = if (partitionAt < 0) emptyList() else arguments.subList(partitionAt + 1, arguments.size)
    if (columnDefinitions.size !in COLUMN_COUNT) return null
    if (partitionAt >= 0 && fieldDefinitions.isEmpty()) return null
    val columns = columnDefinitions.map { parseColumn(it) ?: return null }
    if (columns.map { it.name }.toSet().size != columns.size) return null
    val fields = fieldDefinitions.map { parseField(it, columns) ?: return null }
    if (fields.map { it.sourceColumn }.toSet().size != fields.size) return null
    return Schema(columns, fields)
}

private fun parseColumn(definition: String): Column? {
    val parts = definition.split(':')
    if (parts.size != 2 || !NAME.matches(parts[0])) return null
    val type = ColumnType.of(parts[1]) ?: return null
    return Column(parts[0], type)
}

private fun parseField(definition: String, columns: List<Column>): PartitionField? {
    val day = DAY_FIELD.matchEntire(definition)
    if (day != null) {
        val source = columns.indexOfFirst { it.name == day.groupValues[1] }
        return if (columns.getOrNull(source)?.type == ColumnType.TIMESTAMP) DayField(source, day.groupValues[1]) else null
    }
    val bucket = BUCKET_FIELD.matchEntire(definition) ?: return null
    val source = columns.indexOfFirst { it.name == bucket.groupValues[2] }
    val sourceType = columns.getOrNull(source)?.type
    if (sourceType != ColumnType.LONG && sourceType != ColumnType.STRING) return null
    return BucketField(source, bucket.groupValues[2], bucket.groupValues[1].toInt())
}

private fun singleName(arguments: List<String>): String? = arguments.singleOrNull()?.takeIf(NAME::matches)

private fun parseAppend(arguments: List<String>, schema: Schema): Command.Append? {
    if (arguments.size < 3) return null
    val (transaction, file) = arguments
    if (!NAME.matches(transaction) || !NAME.matches(file)) return null
    val rows = arguments.drop(2).map { parseRow(it, schema.columns) ?: return null }
    return Command.Append(transaction, file, rows)
}

private fun parseRow(text: String, columns: List<Column>): Row? {
    val values = text.split(',')
    if (values.size != columns.size) return null
    return values.mapIndexed { index, value -> columns[index].type.parse(value) ?: return null }
}

private fun parseRemove(arguments: List<String>): Command.Remove? {
    if (arguments.size != 2 || !arguments.all(NAME::matches)) return null
    return Command.Remove(transaction = arguments[0], file = arguments[1])
}

private fun parseCompact(arguments: List<String>): Command.Compact? {
    if (arguments.size < 3 || !arguments.all(NAME::matches)) return null
    val replacedFiles = arguments.drop(2)
    if (replacedFiles.toSet().size != replacedFiles.size) return null
    return Command.Compact(transaction = arguments[0], newFile = arguments[1], replacedFiles = replacedFiles)
}

private fun parsePositionDelete(arguments: List<String>): Command.PositionDelete? {
    if (arguments.size < 4 || !arguments.take(3).all(NAME::matches)) return null
    val positionTexts = arguments.drop(3)
    if (!positionTexts.all(POSITION::matches)) return null
    val positions = positionTexts.map(String::toInt)
    if (positions.zipWithNext().any { (previous, next) -> next <= previous }) return null
    return Command.PositionDelete(arguments[0], deleteFile = arguments[1], dataFile = arguments[2], positions = positions)
}

private fun parseEqualityDelete(arguments: List<String>, schema: Schema): Command.EqualityDelete? {
    if (arguments.size < 4 || !NAME.matches(arguments[0]) || !NAME.matches(arguments[1])) return null
    val columnList = arguments[2]
    val columns = columnList.split(',').map { schema.columnIndex(it) ?: return null }
    if (columns.toSet().size != columns.size) return null
    if (schema.fields.any { it.sourceColumn !in columns }) return null
    val keyColumns = columns.map { schema.columns[it] }
    val rows = arguments.drop(3).map { parseRow(it, keyColumns) ?: return null }
    return Command.EqualityDelete(arguments[0], arguments[1], columnList, columns, rows)
}

private fun parseExpire(arguments: List<String>): Command.Expire? {
    if (arguments.isEmpty() || !arguments.all(NAME::matches)) return null
    if (arguments.toSet().size != arguments.size) return null
    return Command.Expire(arguments)
}

private fun parseScan(arguments: List<String>, schema: Schema): Command.Scan? {
    val readsSnapshot = arguments.firstOrNull() == AT
    val snapshot = if (readsSnapshot) arguments.getOrNull(1)?.takeIf(NAME::matches) ?: return null else null
    val clause = arguments.drop(if (readsSnapshot) 2 else 0)
    if (clause.isEmpty()) return Command.Scan(snapshot, emptyList())
    if (clause.first() != WHERE) return null
    val conditions = parseConditions(clause.drop(1), schema) ?: return null
    return Command.Scan(snapshot, conditions)
}

private fun parseConditions(words: List<String>, schema: Schema): List<Condition>? {
    if (words.size % 2 == 0) return null
    if (words.indices.any { it % 2 == 1 && words[it] != AND }) return null
    return words.filterIndexed { index, _ -> index % 2 == 0 }.map { parseCondition(it, schema) ?: return null }
}

private fun parseCondition(text: String, schema: Schema): Condition? {
    val columnName = text.takeWhile { it in 'a'..'z' || it in '0'..'9' }
    val column = schema.columnIndex(columnName) ?: return null
    val rest = text.substring(columnName.length)
    val operator = Operator.entries.filter { rest.startsWith(it.symbol) }.maxByOrNull { it.symbol.length } ?: return null
    val value = schema.columns[column].type.parse(rest.substring(operator.symbol.length)) ?: return null
    return Condition(column, operator, value)
}
