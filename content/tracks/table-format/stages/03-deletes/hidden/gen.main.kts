import kotlin.random.Random

class Options(
    val transactions: Int,
    val files: Int,
    val rows: Int,
    val active: Int,
    val scans: Int,
    val seed: Long,
)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--transactions" to "30",
        "--files" to "3",
        "--rows" to "3",
        "--active" to "3",
        "--scans" to "20",
        "--seed" to "1",
    )
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    val options = Options(
        transactions = values.getValue("--transactions").toInt(),
        files = values.getValue("--files").toInt(),
        rows = values.getValue("--rows").toInt(),
        active = values.getValue("--active").toInt(),
        scans = values.getValue("--scans").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.transactions >= 1 && options.files >= 1 && options.rows >= 1 && options.active >= 1) {
        "--transactions, --files, --rows, --active 는 1 이상이다"
    }
    require(options.scans >= 0) { "--scans 는 0 이상이다" }
    return options
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.transactions >= 1_000
val output = StringBuilder()

fun emit(line: String) {
    output.append(line).append('\n')
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

fun spaced(line: String): String =
    if (!largeScale && chance(0.05)) "  " + line.replace(" ", pick(listOf("  ", "   "))) + " " else line

enum class Type(val keyword: String) { LONG("long"), STRING("string"), TIMESTAMP("timestamp") }

class Column(val name: String, val type: Type)

sealed interface Field {
    val source: Int
}

class DayField(override val source: Int) : Field

class BucketField(override val source: Int, val buckets: Int) : Field

class Schema(val columns: List<Column>, val fields: List<Field>, val createLine: String)

fun schemaOf(columnText: String, partitionText: String = ""): Schema {
    val columns = columnText.split(" ").map { definition ->
        val (name, keyword) = definition.split(":")
        Column(name, Type.entries.first { it.keyword == keyword })
    }
    val fields = if (partitionText.isEmpty()) emptyList() else partitionText.split(" ").map { field ->
        val source = columns.indexOfFirst { field.endsWith("(${it.name})") }
        if (field.startsWith("day")) DayField(source) else BucketField(source, field.substringAfter("[").substringBefore("]").toInt())
    }
    val partitionClause = if (partitionText.isEmpty()) "" else " PARTITION $partitionText"
    return Schema(columns, fields, "CREATE $columnText$partitionClause")
}

val LARGE_SCHEMA = schemaOf("id:long kind:string", "bucket[8](id)")
val SCHEMAS = listOf(
    schemaOf("id:long kind:string", "bucket[2](id)") to 5,
    schemaOf("id:long kind:string") to 3,
    schemaOf("ts:timestamp id:long kind:string", "day(ts) bucket[2](id)") to 2,
    schemaOf("name:string score:long", "bucket[3](name)") to 2,
    schemaOf("a:long b:string c:long", "bucket[2](a) bucket[2](b)") to 1,
)
val schema = if (largeScale) LARGE_SCHEMA else weighted(*SCHEMAS.toTypedArray())
val partitionSources: List<Int> = schema.fields.map { it.source }

fun bucketHash(text: String): Int = text.foldIndexed(0) { index, hash, char -> hash + (index + 1) * char.code }

fun partitionOf(values: List<String>, columns: List<Int> = schema.columns.indices.toList()): String =
    schema.fields.joinToString(",") { field ->
        val value = values[columns.indexOf(field.source)]
        when (field) {
            is DayField -> value.substring(0, 10)
            is BucketField -> (bucketHash(value) % field.buckets).toString()
        }
    }

val COMMON_WORDS = listOf("x", "y", "z", "w")
val RARE_WORDS = listOf("0", "007", "abcdefghijklmnop", "zz")

fun twoDigits(number: Int): String = number.toString().padStart(2, '0')

fun clock(secondOfDay: Int): String =
    "${twoDigits(secondOfDay / 3_600)}:${twoDigits(secondOfDay / 60 % 60)}:${twoDigits(secondOfDay % 60)}"

fun valueOf(type: Type): String = when (type) {
    Type.LONG -> if (chance(0.92)) random.nextInt(0, 12).toString() else pick(listOf("-1", "999999999999999999", "-7"))
    Type.STRING -> if (chance(0.85)) pick(COMMON_WORDS) else pick(RARE_WORDS)
    Type.TIMESTAMP -> "${pick(listOf("2024-03-01", "2024-03-02", "2024-02-29"))}T${if (chance(0.2)) "00:00:00" else clock(random.nextInt(86_400))}"
}

fun randomRow(): List<String> = schema.columns.map { valueOf(it.type) }

fun rowInPartition(partition: String): List<String>? {
    repeat(200) {
        val row = randomRow()
        if (partitionOf(row) == partition) return row
    }
    return null
}

val NOT_COMMITTED = -1

open class Lifetime {
    var addedAt = NOT_COMMITTED
    var removedAt = Int.MAX_VALUE
    var sequenceNumber = NOT_COMMITTED

    fun isLiveAt(snapshotSequence: Int): Boolean = addedAt in 1..snapshotSequence && snapshotSequence < removedAt
}

class DataFileModel(val name: String, val partition: String, val rows: List<List<String>>, val origin: String) : Lifetime()

class PositionDeleteModel(val name: String, val target: DataFileModel, val positions: List<Int>) : Lifetime()

class EqualityDeleteModel(val name: String, val columns: List<Int>, val rows: List<List<String>>, val partition: String)

enum class Plan { MIXED, APPEND, DELETE, COMPACT, REMOVE }

class TransactionModel(
    val name: String,
    val baseVersion: Int,
    val startSequence: Int,
    val plan: Plan,
    var steps: Int,
    val bucket: Int,
) {
    val writtenData = mutableListOf<DataFileModel>()
    val compactedNames = linkedSetOf<String>()
    val positionDeletes = mutableListOf<PositionDeleteModel>()
    val equalityDeletes = mutableListOf<EqualityDeleteModel>()
    val removed = LinkedHashMap<String, DataFileModel>()
    val replaced = linkedSetOf<String>()
    val positionTargets = LinkedHashMap<String, DataFileModel>()
    val isEmpty: Boolean
        get() = writtenData.isEmpty() && positionDeletes.isEmpty() && equalityDeletes.isEmpty() && removed.isEmpty()
}

class FilePool {
    private val files = ArrayList<DataFileModel>()
    private val positions = HashMap<String, Int>()

    val size: Int get() = files.size

    fun add(file: DataFileModel) {
        positions[file.name] = files.size
        files += file
    }

    fun remove(file: DataFileModel) {
        val position = positions.remove(file.name) ?: return
        val last = files.removeAt(files.size - 1)
        if (position < files.size) {
            files[position] = last
            positions[last.name] = position
        }
    }

    fun randomFile(): DataFileModel? = if (files.isEmpty()) null else files[random.nextInt(files.size)]
}

val active = mutableListOf<TransactionModel>()
val endedNames = mutableListOf<String>()
val begunNames = linkedSetOf<String>()
val snapshotAt = HashMap<Int, String>()
val retainedSnapshots = mutableListOf<String>()
val expiredSnapshots = mutableListOf<String>()
val writtenFileNames = linkedSetOf<String>()
val deleteFileNames = mutableListOf<String>()
val committedData = mutableListOf<DataFileModel>()
val liveData = FilePool()
val positionDeletesByTarget = HashMap<String, MutableList<PositionDeleteModel>>()
val equalityKeys = HashMap<String, HashMap<List<Int>, HashMap<List<String>, MutableList<Int>>>>()
val knownRows = mutableListOf<List<String>>()
var version = 1
var sequence = 0
var commits = 0
var fileCounter = 0
var transactionCounter = 0

val NEVER_BEGUN = listOf("ghost", "nobody", "q0")

fun freshTransactionName(): String {
    while (true) {
        val name = if (largeScale) "t${(transactionCounter++).toString(36)}" else "t${++transactionCounter}"
        if (name !in begunNames) return name
    }
}

fun freshFileName(prefix: String): String {
    while (true) {
        val name = if (largeScale) "$prefix${(fileCounter++).toString(36)}" else "$prefix${++fileCounter}"
        if (name !in writtenFileNames) return name
    }
}

fun isRowDeleted(file: DataFileModel, position: Int, row: List<String>, snapshotSequence: Int): Boolean {
    val byPosition = positionDeletesByTarget[file.name].orEmpty().any {
        it.isLiveAt(snapshotSequence) && file.sequenceNumber <= it.sequenceNumber && position in it.positions
    }
    if (byPosition) return true
    val groups = equalityKeys[file.partition] ?: return false
    return groups.any { (columns, keys) ->
        keys[columns.map { row[it] }].orEmpty().any { it > file.sequenceNumber && it <= snapshotSequence }
    }
}

fun survivingRows(file: DataFileModel, snapshotSequence: Int): List<List<String>> =
    file.rows.filterIndexed { position, row -> !isRowDeleted(file, position, row, snapshotSequence) }

fun liveDataAtStart(transaction: TransactionModel): List<DataFileModel> =
    committedData.filter { it.isLiveAt(transaction.startSequence) }

fun removableAtStart(transaction: TransactionModel): List<DataFileModel> =
    liveDataAtStart(transaction).filter { it.name !in transaction.removed && it.name !in transaction.positionTargets }

fun positionTargetsFor(transaction: TransactionModel): List<DataFileModel> =
    liveDataAtStart(transaction).filter { it.name !in transaction.removed } + transaction.writtenData

fun begin(plan: Plan, steps: Int, bucket: Int = 0): TransactionModel {
    val name = freshTransactionName()
    begunNames += name
    emit(spaced("BEGIN $name"))
    return TransactionModel(name, version, sequence, plan, steps, bucket).also { active += it }
}

fun appendFile(transaction: TransactionModel, rows: List<List<String>>) {
    val name = freshFileName("f")
    emit(spaced("APPEND ${transaction.name} $name ${rows.joinToString(" ") { it.joinToString(",") }}"))
    writtenFileNames += name
    knownRows += rows
    transaction.writtenData += DataFileModel(name, partitionOf(rows.first()), rows, transaction.name)
}

fun removeFile(transaction: TransactionModel, file: DataFileModel) {
    emit(spaced("REMOVE ${transaction.name} ${file.name}"))
    transaction.removed[file.name] = file
}

fun compactFiles(transaction: TransactionModel, replaced: List<DataFileModel>): Boolean {
    val rows = replaced.flatMap { survivingRows(it, transaction.startSequence) }
    val name = freshFileName("g")
    emit(spaced("COMPACT ${transaction.name} $name ${replaced.joinToString(" ") { it.name }}"))
    if (rows.isEmpty()) return false
    writtenFileNames += name
    replaced.forEach {
        transaction.removed[it.name] = it
        transaction.replaced += it.name
    }
    transaction.writtenData += DataFileModel(name, replaced.first().partition, rows, transaction.name)
    transaction.compactedNames += name
    return true
}

fun deletePositions(transaction: TransactionModel, target: DataFileModel, positions: List<Int>) {
    val name = freshFileName("x")
    emit(spaced("POSDEL ${transaction.name} $name ${target.name} ${positions.joinToString(" ")}"))
    writtenFileNames += name
    deleteFileNames += name
    transaction.positionDeletes += PositionDeleteModel(name, target, positions)
    transaction.positionTargets[target.name] = target
}

fun deleteEqualRows(transaction: TransactionModel, columns: List<Int>, rows: List<List<String>>) {
    val name = freshFileName("e")
    val columnText = columns.joinToString(",") { schema.columns[it].name }
    emit(spaced("EQDEL ${transaction.name} $name $columnText ${rows.joinToString(" ") { it.joinToString(",") }}"))
    writtenFileNames += name
    deleteFileNames += name
    transaction.equalityDeletes += EqualityDeleteModel(name, columns, rows, partitionOf(rows.first(), columns))
}

fun conflicts(transaction: TransactionModel): Boolean {
    val writtenNames = transaction.writtenData.map { it.name }.toSet()
    return transaction.removed.values.any { !it.isLiveAt(sequence) } ||
        transaction.positionTargets.values.any { it.name !in writtenNames && !it.isLiveAt(sequence) } ||
        transaction.replaced.any { replaced ->
            positionDeletesByTarget[replaced].orEmpty().any { it.isLiveAt(sequence) && it.sequenceNumber > transaction.startSequence }
        }
}

fun commit(transaction: TransactionModel) {
    emit(spaced("COMMIT ${transaction.name}"))
    if (transaction.isEmpty) return
    active -= transaction
    endedNames += transaction.name
    if (transaction.baseVersion != version && conflicts(transaction)) return
    val parentSequence = sequence
    sequence++
    version++
    commits++
    for (file in transaction.removed.values) {
        file.removedAt = sequence
        liveData.remove(file)
        positionDeletesByTarget[file.name].orEmpty().filter { it.isLiveAt(parentSequence) }.forEach { it.removedAt = sequence }
    }
    for (file in transaction.writtenData) {
        file.addedAt = sequence
        file.sequenceNumber = if (file.name in transaction.compactedNames) transaction.startSequence else sequence
        committedData += file
        liveData.add(file)
    }
    for (delete in transaction.positionDeletes) {
        delete.addedAt = sequence
        delete.sequenceNumber = sequence
        positionDeletesByTarget.getOrPut(delete.target.name) { mutableListOf() } += delete
    }
    for (delete in transaction.equalityDeletes) {
        val keys = equalityKeys.getOrPut(delete.partition) { HashMap() }.getOrPut(delete.columns) { HashMap() }
        for (row in delete.rows) keys.getOrPut(row) { mutableListOf() } += sequence
    }
    snapshotAt[sequence] = transaction.name
    retainedSnapshots += transaction.name
}

fun abort(transaction: TransactionModel) {
    emit(spaced("ABORT ${transaction.name}"))
    active -= transaction
    endedNames += transaction.name
}

fun protectedSnapshots(): Set<String> =
    (active.mapNotNull { snapshotAt[it.startSequence] } + listOfNotNull(retainedSnapshots.lastOrNull())).toSet()

fun expirable(): List<String> {
    val protected = protectedSnapshots()
    return retainedSnapshots.filter { it !in protected }
}

fun expire(names: List<String>) {
    emit(spaced("EXPIRE ${names.joinToString(" ")}"))
    retainedSnapshots.removeAll(names.toSet())
    expiredSnapshots += names
    version++
}

fun positionsOf(file: DataFileModel): List<Int> =
    file.rows.indices.shuffled(random).take(1 + random.nextInt(minOf(3, file.rows.size))).sorted()

fun equalityColumns(): List<Int> {
    val extra = schema.columns.indices.filter { it !in partitionSources }.shuffled(random)
    val chosen = (partitionSources + extra.take(if (partitionSources.isEmpty()) 1 + random.nextInt(extra.size) else random.nextInt(extra.size + 1))).distinct()
    return if (chance(0.5)) chosen.shuffled(random) else chosen.sorted()
}

fun equalityRows(columns: List<Int>, partition: String?): List<List<String>>? {
    val source = knownRows.filter { partition == null || partitionOf(it) == partition }.ifEmpty { return null }
    val anchor = pick(source)
    val anchorPartition = partitionOf(anchor)
    val sameRows = source.filter { partitionOf(it) == anchorPartition }
    return List(1 + random.nextInt(3)) { if (chance(0.8)) pick(sameRows) else rowInPartition(anchorPartition) ?: anchor }
        .map { row -> columns.map { row[it] } }
}

fun knownValue(column: Int): String =
    if (knownRows.isNotEmpty() && chance(0.75)) pick(knownRows)[column] else valueOf(schema.columns[column].type)

fun condition(): String {
    val column = if (partitionSources.isNotEmpty() && chance(0.5)) pick(partitionSources) else random.nextInt(schema.columns.size)
    val operator = weighted("=" to 4, "<" to 1, "<=" to 1, ">" to 1, ">=" to 1)
    return "${schema.columns[column].name}$operator${knownValue(column)}"
}

fun snapshotChoice(): String = weighted(
    { if (retainedSnapshots.isEmpty()) "ghost" else pick(retainedSnapshots) } to 7,
    { if (expiredSnapshots.isEmpty()) "nobody" else pick(expiredSnapshots) } to 1,
    { pick(NEVER_BEGUN + endedNames.takeLast(2)) } to 1,
)()

fun scan() {
    val target = if (chance(0.35)) "AT ${snapshotChoice()} " else ""
    val conditions = List(weighted(0 to 3, 1 to 4, 2 to 1)) { condition() }
    val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
    emit(spaced("SCAN $target$where".trimEnd()))
}

fun query() {
    val names = endedNames + retainedSnapshots
    val manifestGuesses = names.flatMap { listOf("$it-m1", "$it-m2", "$it-m3") }
    when (weighted("manifests" to 4, "entries" to 5, "snapshots" to 1, "ls" to 1)) {
        "manifests" -> emit(spaced("MANIFESTS ${snapshotChoice()}"))
        "entries" -> emit(spaced("ENTRIES ${if (manifestGuesses.isEmpty()) "a-m1" else pick(manifestGuesses)}"))
        "snapshots" -> emit(spaced("SNAPSHOTS"))
        else -> emit(spaced("LS"))
    }
}

fun stepSmall(transaction: TransactionModel) {
    val removable = removableAtStart(transaction)
    val targets = positionTargetsFor(transaction)
    val groups = removable.groupBy { it.partition }.values.toList()
    when (
        weighted(
            "append" to 4,
            "remove" to if (removable.isEmpty()) 0 else 2,
            "compact" to if (groups.isEmpty()) 0 else 3,
            "posdel" to if (targets.isEmpty()) 0 else 4,
            "eqdel" to if (knownRows.isEmpty()) 0 else 3,
            "bad" to 4,
        )
    ) {
        "append" -> {
            val anchor = randomRow()
            appendFile(transaction, listOf(anchor) + List(random.nextInt(0, options.rows)) { rowInPartition(partitionOf(anchor)) ?: anchor })
        }
        "remove" -> removeFile(transaction, pick(removable))
        "compact" -> {
            val group = pick(groups).shuffled(random)
            compactFiles(transaction, group.take(1 + random.nextInt(minOf(group.size, 3))))
        }
        "posdel" -> pick(targets).let { deletePositions(transaction, it, positionsOf(it)) }
        "eqdel" -> {
            val columns = equalityColumns()
            equalityRows(columns, null)?.let { deleteEqualRows(transaction, columns, it) }
        }
        else -> badTransactionLine(transaction)
    }
    transaction.steps--
}

fun badTransactionLine(transaction: TransactionModel) {
    val name = transaction.name
    val removable = removableAtStart(transaction)
    val target = positionTargetsFor(transaction).randomOrNull(random)
    val anyDeleteFile = deleteFileNames.randomOrNull(random)
    val notLiveData = listOfNotNull(
        transaction.positionTargets.keys.randomOrNull(random),
        transaction.removed.keys.randomOrNull(random),
        committedData.filter { !it.isLiveAt(transaction.startSequence) }.randomOrNull(random)?.name,
        anyDeleteFile,
        "nosuch",
    )
    val firstColumn = schema.columns.first()
    val columnsWithoutPartition = schema.columns.indices.filter { it !in partitionSources }
    when (
        weighted(
            "posdelShape" to 3,
            "posdelExists" to 2,
            "posdelNotLive" to 2,
            "posdelBadPosition" to if (target == null) 0 else 4,
            "eqdelShape" to 3,
            "eqdelExists" to 2,
            "eqdelMixed" to if (schema.fields.isEmpty()) 0 else 3,
            "deleteNameReused" to if (anyDeleteFile == null) 0 else 2,
            "removeNotLive" to 2,
            "compactNotLive" to 2,
            "compactNoRows" to 1,
        )
    ) {
        "posdelShape" -> {
            val data = target?.name ?: "f1"
            emit(pick(listOf("POSDEL $name x99 $data 1 0", "POSDEL $name x99 $data 0 0", "POSDEL $name x99 $data", "POSDEL $name x99 $data -1", "POSDEL $name x99 $data 01", "POSDEL $name x99 $data a", "POSDEL $name X99 $data 0", "POSDEL $name x99 $data 1000000000", "POSDEL $name")))
        }
        "posdelExists" -> emit(spaced("POSDEL $name ${pick(writtenFileNames.toList().ifEmpty { listOf("f1") })} ${target?.name ?: "f1"} 0"))
        "posdelNotLive" -> emit(spaced("POSDEL $name ${freshFileName("x")} ${pick(notLiveData)} 0"))
        "posdelBadPosition" -> emit(spaced("POSDEL $name ${freshFileName("x")} ${target!!.name} ${target.rows.size + random.nextInt(0, 2)}"))
        "eqdelShape" -> {
            val partitionColumn = schema.columns[partitionSources.firstOrNull() ?: 0].name
            val other = columnsWithoutPartition.firstOrNull()?.let { schema.columns[it].name } ?: partitionColumn
            emit(
                pick(
                    listOf(
                        "EQDEL $name e99 $other ${valueOf(schema.columns[schema.columns.indexOfFirst { it.name == other }].type)}",
                        "EQDEL $name e99 $partitionColumn,$partitionColumn 1,1",
                        "EQDEL $name e99 nosuch 1",
                        "EQDEL $name e99 ${firstColumn.name}",
                        "EQDEL $name e99 ${firstColumn.name}, 1",
                        "EQDEL $name e99 ${schema.columns.joinToString(",") { it.name }} ${valueOf(firstColumn.type)}",
                        "EQDEL $name E99 ${firstColumn.name} ${valueOf(firstColumn.type)}",
                    ),
                ),
            )
        }
        "eqdelExists" -> {
            val columns = equalityColumns()
            emit(spaced("EQDEL $name ${pick(writtenFileNames.toList().ifEmpty { listOf("f1") })} ${columns.joinToString(",") { schema.columns[it].name }} ${columns.joinToString(",") { valueOf(schema.columns[it].type) }}"))
        }
        "eqdelMixed" -> {
            val columns = equalityColumns()
            val first = randomRow()
            val second = (0 until 50).map { randomRow() }.firstOrNull { partitionOf(it) != partitionOf(first) } ?: return
            emit(spaced("EQDEL $name ${freshFileName("e")} ${columns.joinToString(",") { schema.columns[it].name }} ${listOf(first, second).joinToString(" ") { row -> columns.joinToString(",") { row[it] } }}"))
        }
        "deleteNameReused" -> {
            val replaced = removable.randomOrNull(random)?.name ?: "f1"
            val reused = if (chance(0.5)) "APPEND $name $anyDeleteFile ${randomRow().joinToString(",")}" else "COMPACT $name $anyDeleteFile $replaced"
            emit(spaced(reused))
        }
        "removeNotLive" -> emit(spaced("REMOVE $name ${pick(notLiveData)}"))
        "compactNotLive" -> {
            val good = removable.shuffled(random).take(random.nextInt(0, 2)).map { it.name }
            emit(spaced("COMPACT $name ${freshFileName("g")} ${(good + pick(notLiveData)).distinct().joinToString(" ")}"))
        }
        else -> {
            val emptied = removable.filter { survivingRows(it, transaction.startSequence).isEmpty() }
            if (emptied.isNotEmpty()) compactFiles(transaction, listOf(pick(emptied)))
        }
    }
}

fun staleLine() {
    val name = if (endedNames.isNotEmpty() && chance(0.7)) pick(endedNames) else pick(NEVER_BEGUN)
    val data = committedData.randomOrNull(random)?.name ?: "f1"
    emit(
        weighted(
            "POSDEL $name ${freshFileName("x")} $data 0" to 2,
            "EQDEL $name ${freshFileName("e")} ${schema.columns.joinToString(",") { it.name }} ${randomRow().joinToString(",")}" to 2,
            "COMMIT $name" to 1,
            "ABORT $name" to 1,
        ),
    )
}

val UNKNOWN_COMMANDS = listOf("posdel", "EqDel", "DELETE", "POSDELETE", "EQUALITY", "VACUUM")

fun noise() {
    emit(weighted("" to 1, pick(UNKNOWN_COMMANDS) to 3, "POSDEL\ta" to 1, "${pick(UNKNOWN_COMMANDS)} a x1 f1 0" to 1))
}

fun finish(transaction: TransactionModel) {
    if (chance(if (transaction.isEmpty) 0.8 else 0.12)) abort(transaction) else commit(transaction)
}

fun runNormal() {
    repeat(random.nextInt(0, 3)) { emit(pick(listOf("POSDEL a x1 f1 0", "EQDEL a e1 id 1", "posdel a", "LS"))) }
    emit(spaced(schema.createLine))
    var begun = 0
    while (begun < options.transactions || active.isNotEmpty()) {
        val canBegin = begun < options.transactions && active.size < options.active
        val action = weighted(
            "begin" to if (canBegin) (if (active.isEmpty()) 100 else 4) else 0,
            "step" to if (active.isEmpty()) 0 else 14,
            "finish" to if (active.isEmpty()) 0 else 4,
            "expire" to if (expirable().isEmpty()) 0 else 1,
            "orphans" to 1,
            "scan" to 1 + options.scans / 4,
            "query" to 3,
            "stale" to 1,
            "noise" to 1,
        )
        when (action) {
            "begin" -> {
                begin(Plan.MIXED, steps = 1 + random.nextInt(options.files + 3))
                begun++
            }
            "step" -> pick(active).let { transaction -> if (transaction.steps > 0) stepSmall(transaction) else finish(transaction) }
            "finish" -> pick(active).let { transaction ->
                if (transaction.steps <= 0 || (!transaction.isEmpty && chance(0.2))) finish(transaction)
            }
            "expire" -> expirable().let { candidates -> expire(candidates.shuffled(random).take(1 + random.nextInt(minOf(2, candidates.size)))) }
            "orphans" -> emit(spaced("ORPHANS"))
            "scan" -> scan()
            "query" -> query()
            "stale" -> staleLine()
            else -> noise()
        }
    }
    emit("SNAPSHOTS")
    repeat(3) { scan() }
    emit("ORPHANS")
    emit("LS")
}

val LARGE_KINDS = listOf("x", "y", "z", "w", "v")
val idsByBucket: Map<Int, List<Long>> by lazy { (1L..25_000L).groupBy { bucketHash(it.toString()) % 8 } }
val bandStarts = HashMap<String, Int>()
val filesByOrigin = HashMap<String, List<DataFileModel>>()
val recentByBucket = HashMap<Int, ArrayDeque<DataFileModel>>()
var pendingScans = 0.0

fun largeRows(transaction: TransactionModel): List<List<String>> {
    val ids = idsByBucket.getValue(transaction.bucket)
    val start = bandStarts.getOrPut(transaction.name) { random.nextInt(ids.size - 16) }
    bandStarts[transaction.name] = (start + 6) % (ids.size - 16)
    return List(options.rows - 2 + random.nextInt(5)) { listOf(ids[start + random.nextInt(8)].toString(), pick(LARGE_KINDS)) }
}

fun isUsableBy(transaction: TransactionModel, candidate: DataFileModel, removable: Boolean): Boolean =
    candidate.isLiveAt(transaction.startSequence) && candidate.name !in transaction.removed &&
        (!removable || candidate.name !in transaction.positionTargets)

fun recentLiveFile(transaction: TransactionModel, removable: Boolean): DataFileModel? {
    val recent = recentByBucket[transaction.bucket] ?: return null
    while (recent.isNotEmpty() && recent.first().removedAt != Int.MAX_VALUE) recent.removeFirst()
    if (recent.isEmpty()) return null
    repeat(30) {
        val candidate = recent[recent.size - 1 - random.nextInt(minOf(recent.size, 600))]
        if (isUsableBy(transaction, candidate, removable)) return candidate
    }
    return null
}

fun overlappingFile(transaction: TransactionModel, removable: Boolean): DataFileModel? = active
    .filter { it !== transaction }
    .flatMap { it.removed.values + it.positionTargets.values }
    .filter { isUsableBy(transaction, it, removable) }
    .randomOrNull(random)

fun stepLarge(transaction: TransactionModel) {
    when (transaction.plan) {
        Plan.APPEND -> appendFile(transaction, largeRows(transaction))
        Plan.DELETE -> {
            val target = (if (transaction.writtenData.isNotEmpty() && chance(0.05)) pick(transaction.writtenData) else null)
                ?: (if (chance(0.15)) overlappingFile(transaction, removable = false) else null)
                ?: recentLiveFile(transaction, removable = false)
            if (target != null && chance(0.67)) {
                deletePositions(transaction, target, positionsOf(target))
            } else if (target != null) {
                val columns = if (chance(0.7)) listOf(0) else listOf(0, 1)
                val rows = List(2 + random.nextInt(5)) { pick(target.rows) }.map { row -> columns.map { row[it] } }
                deleteEqualRows(transaction, columns, rows)
            }
        }
        Plan.COMPACT -> {
            val first = (if (chance(0.25)) overlappingFile(transaction, removable = true) else null)
                ?: recentLiveFile(transaction, removable = true)
            if (first != null) {
                val neighbors = filesByOrigin[first.origin].orEmpty()
                    .filter { it !== first && isUsableBy(transaction, it, removable = true) }
                    .shuffled(random)
                    .take(2 + random.nextInt(6))
                compactFiles(transaction, listOf(first) + neighbors)
            }
        }
        else -> {
            val target = (if (chance(0.15)) overlappingFile(transaction, removable = true) else null)
                ?: recentLiveFile(transaction, removable = true)
            if (target != null) removeFile(transaction, target)
        }
    }
    transaction.steps--
}

fun largeScan() {
    val target = if (retainedSnapshots.size > 1 && chance(0.5)) "AT ${pick(retainedSnapshots)} " else ""
    val ids = idsByBucket.getValue(random.nextInt(8))
    val reference = liveData.randomFile()
    val id = if (reference != null && chance(0.7)) pick(reference.rows)[0].toLong() else ids[random.nextInt(ids.size)]
    val condition = if (chance(0.95)) "id=$id" else "id>=$id AND id<=${id + random.nextInt(1, 8)}"
    emit("SCAN ${target}WHERE $condition")
}

fun endLarge(transaction: TransactionModel) {
    if (transaction.isEmpty || chance(0.02)) {
        abort(transaction)
    } else {
        val before = commits
        commit(transaction)
        if (commits > before) {
            filesByOrigin[transaction.name] = transaction.writtenData.toList()
            transaction.writtenData.forEach { recentByBucket.getOrPut(transaction.bucket) { ArrayDeque() }.addLast(it) }
        }
    }
    pendingScans += options.scans.toDouble() / options.transactions
    while (pendingScans >= 1) {
        largeScan()
        pendingScans -= 1
    }
}

fun runLarge() {
    emit(schema.createLine)
    var begun = 0
    while (begun < options.transactions || active.isNotEmpty()) {
        val canBegin = begun < options.transactions && active.size < options.active
        if (active.isEmpty() || (canBegin && chance(0.01))) {
            val plan = if (liveData.size < 300) Plan.APPEND else weighted(Plan.APPEND to 25, Plan.DELETE to 20, Plan.COMPACT to 13, Plan.REMOVE to 42)
            val steps = when (plan) {
                Plan.APPEND -> options.files - 6 + random.nextInt(13)
                Plan.DELETE -> 15 + random.nextInt(21)
                Plan.COMPACT -> 1 + random.nextInt(3)
                else -> 4 + random.nextInt(9)
            }
            begin(plan, steps, random.nextInt(8))
            begun++
            continue
        }
        val transaction = if (chance(0.9)) active.first() else pick(active)
        if (transaction.steps > 0) stepLarge(transaction) else endLarge(transaction)
    }
    emit("SNAPSHOTS")
}

if (largeScale) runLarge() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
