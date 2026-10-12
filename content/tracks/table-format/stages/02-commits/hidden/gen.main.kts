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
        "--rows" to "2",
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

val LARGE_SCHEMA = schemaOf("id:long name:string", "bucket[8](id)")
val SCHEMAS = listOf(
    schemaOf("id:long name:string", "bucket[4](id)") to 5,
    schemaOf("id:long name:string") to 3,
    schemaOf("ts:timestamp id:long kind:string", "day(ts) bucket[2](id)") to 3,
    schemaOf("name:string score:long", "bucket[3](name)") to 2,
    schemaOf("ts:timestamp v:long", "day(ts)") to 1,
    schemaOf("a:long b:string c:timestamp", "bucket[1](a)") to 1,
)
val schema = if (largeScale) LARGE_SCHEMA else weighted(*SCHEMAS.toTypedArray())

fun bucketHash(text: String): Int = text.foldIndexed(0) { index, hash, char -> hash + (index + 1) * char.code }

fun partitionOf(row: List<String>): String = schema.fields.joinToString(",") { field ->
    when (field) {
        is DayField -> row[field.source].substring(0, 10)
        is BucketField -> (bucketHash(row[field.source]) % field.buckets).toString()
    }
}

val COMMON_WORDS = listOf("ann", "bob", "cat", "dan", "eve")
val RARE_WORDS = listOf("0", "007", "a", "zzzzzzzzzzzzzzzz", "abcdefghijklmnop", "x1")
val COMMON_DAYS = listOf("2024-03-01", "2024-03-02")
val RARE_DAYS = listOf("2024-02-29", "1970-01-01", "9999-12-31")

fun twoDigits(number: Int): String = number.toString().padStart(2, '0')

fun clock(secondOfDay: Int): String =
    "${twoDigits(secondOfDay / 3_600)}:${twoDigits(secondOfDay / 60 % 60)}:${twoDigits(secondOfDay % 60)}"

fun longValue(): String = weighted(
    { random.nextInt(0, 24).toString() } to 10,
    { (-random.nextInt(1, 9)).toString() } to 1,
    { pick(listOf("0", "999999999999999999", "-999999999999999999")) } to 1,
)()

fun stringValue(): String = if (chance(0.85)) pick(COMMON_WORDS) else pick(RARE_WORDS)

fun timestampValue(day: String = if (chance(0.85)) pick(COMMON_DAYS) else pick(RARE_DAYS)): String =
    "${day}T${if (chance(0.2)) pick(listOf("00:00:00", "23:59:59")) else clock(random.nextInt(86_400))}"

fun valueOf(type: Type): String = when (type) {
    Type.LONG -> longValue()
    Type.STRING -> stringValue()
    Type.TIMESTAMP -> timestampValue()
}

fun randomRow(): List<String> = schema.columns.map { valueOf(it.type) }

fun sameBucketValue(anchorValue: String, type: Type, buckets: Int): String {
    val target = bucketHash(anchorValue) % buckets
    repeat(100) {
        val candidate = valueOf(type)
        if (bucketHash(candidate) % buckets == target) return candidate
    }
    return anchorValue
}

fun rowLike(anchor: List<String>): List<String> {
    val row = randomRow().toMutableList()
    for (field in schema.fields) {
        val source = field.source
        row[source] = when (field) {
            is DayField -> timestampValue(anchor[source].substring(0, 10))
            is BucketField -> sameBucketValue(anchor[source], schema.columns[source].type, field.buckets)
        }
    }
    return row
}

val NOT_COMMITTED = -1

class FileModel(val name: String, val partition: String, val rows: List<String>, val origin: String = "") {
    var addedAt = NOT_COMMITTED
    var removedAt = Int.MAX_VALUE

    fun isLiveAt(sequenceNumber: Int): Boolean = addedAt in 1..sequenceNumber && sequenceNumber < removedAt
}

enum class Plan { APPEND, REMOVE, COMPACT, MIXED }

class TransactionModel(
    val name: String,
    val baseVersion: Int,
    val startSequence: Int,
    val plan: Plan,
    var steps: Int,
    val buckets: List<Int>,
) {
    val written = mutableListOf<FileModel>()
    val removed = LinkedHashMap<String, FileModel>()
    val isEmpty: Boolean get() = written.isEmpty() && removed.isEmpty()
}

val active = mutableListOf<TransactionModel>()
val endedNames = mutableListOf<String>()
val begunNames = linkedSetOf<String>()
val snapshotAt = HashMap<Int, String>()
val retainedSnapshots = mutableListOf<String>()
val expiredSnapshots = mutableListOf<String>()
val writtenFileNames = linkedSetOf<String>()
val committedFiles = mutableListOf<FileModel>()
val liveFileNames = HashSet<String>()
val knownRows = mutableListOf<List<String>>()
var version = 1
var sequence = 0
var commits = 0
var fileCounter = 0
var transactionCounter = 0

val SPECIAL_TRANSACTIONS = listOf("a", "b", "w", "abcdefghijklmnop", "meta", "f1")
val SPECIAL_FILES = listOf("g1", "abcdefghijklmnop", "z", "data9")
val NEVER_BEGUN = listOf("ghost", "nobody", "q0")

fun freshTransactionName(): String {
    if (!largeScale && chance(0.1)) SPECIAL_TRANSACTIONS.firstOrNull { it !in begunNames }?.let { return it }
    while (true) {
        val name = if (largeScale) "t${(transactionCounter++).toString(36)}" else "t${++transactionCounter}"
        if (name !in begunNames) return name
    }
}

fun freshFileName(): String {
    if (!largeScale && chance(0.06)) SPECIAL_FILES.firstOrNull { it !in writtenFileNames }?.let { return it }
    while (true) {
        val name = if (largeScale) "f${(fileCounter++).toString(36)}" else "f${++fileCounter}"
        if (name !in writtenFileNames) return name
    }
}

fun startSnapshotOf(transaction: TransactionModel): String? = snapshotAt[transaction.startSequence]

fun removableAtStart(transaction: TransactionModel): List<FileModel> =
    committedFiles.filter { it.isLiveAt(transaction.startSequence) && it.name !in transaction.removed }

fun begin(plan: Plan, steps: Int, buckets: List<Int> = emptyList()): TransactionModel {
    val name = freshTransactionName()
    begunNames += name
    emit(spaced("BEGIN $name"))
    return TransactionModel(name, version, sequence, plan, steps, buckets).also { active += it }
}

fun appendFile(transaction: TransactionModel, rows: List<List<String>>) {
    val name = freshFileName()
    val texts = rows.map { it.joinToString(",") }
    emit(spaced("APPEND ${transaction.name} $name ${texts.joinToString(" ")}"))
    writtenFileNames += name
    knownRows += rows
    transaction.written += FileModel(name, partitionOf(rows.first()), texts, transaction.name)
}

fun appendRandomFile(transaction: TransactionModel) {
    val anchor = randomRow()
    appendFile(transaction, listOf(anchor) + List(random.nextInt(0, options.rows)) { rowLike(anchor) })
}

fun removeFile(transaction: TransactionModel, file: FileModel) {
    emit(spaced("REMOVE ${transaction.name} ${file.name}"))
    transaction.removed[file.name] = file
}

fun compactFiles(transaction: TransactionModel, replaced: List<FileModel>) {
    val name = freshFileName()
    emit(spaced("COMPACT ${transaction.name} $name ${replaced.joinToString(" ") { it.name }}"))
    writtenFileNames += name
    replaced.forEach { transaction.removed[it.name] = it }
    transaction.written += FileModel(name, replaced.first().partition, replaced.flatMap { it.rows }, transaction.name)
}

fun commit(transaction: TransactionModel) {
    emit(spaced("COMMIT ${transaction.name}"))
    if (transaction.isEmpty) return
    active -= transaction
    endedNames += transaction.name
    val stale = transaction.baseVersion != version
    if (stale && transaction.removed.values.any { !it.isLiveAt(sequence) }) return
    sequence++
    version++
    commits++
    for (file in transaction.removed.values) {
        file.removedAt = sequence
        liveFileNames -= file.name
    }
    for (file in transaction.written) {
        file.addedAt = sequence
        committedFiles += file
        liveFileNames += file.name
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
    (active.mapNotNull(::startSnapshotOf) + listOfNotNull(retainedSnapshots.lastOrNull())).toSet()

fun expire(names: List<String>) {
    emit(spaced("EXPIRE ${names.joinToString(" ")}"))
    retainedSnapshots.removeAll(names.toSet())
    expiredSnapshots += names
    version++
}

fun expirable(): List<String> {
    val protected = protectedSnapshots()
    return retainedSnapshots.filter { it !in protected }
}

fun knownValue(column: Int): String =
    if (knownRows.isNotEmpty() && chance(0.7)) pick(knownRows)[column] else valueOf(schema.columns[column].type)

fun condition(): String {
    val sources = schema.fields.map { it.source }
    val column = if (sources.isNotEmpty() && chance(0.6)) pick(sources) else random.nextInt(schema.columns.size)
    val operator = weighted("=" to 4, "<" to 1, "<=" to 1, ">" to 1, ">=" to 1)
    return "${schema.columns[column].name}$operator${knownValue(column)}"
}

fun snapshotChoice(): String = weighted(
    { if (retainedSnapshots.isEmpty()) "ghost" else pick(retainedSnapshots) } to 6,
    { if (expiredSnapshots.isEmpty()) "nobody" else pick(expiredSnapshots) } to 2,
    { if (endedNames.isEmpty()) "q0" else pick(endedNames) } to 1,
    { pick(NEVER_BEGUN + active.map { it.name }) } to 1,
)()

fun scan() {
    val target = if (chance(0.4)) "AT ${snapshotChoice()} " else ""
    val conditions = List(weighted(0 to 1, 1 to 4, 2 to 2)) { condition() }
    val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
    emit(spaced("SCAN $target$where".trimEnd()))
}

fun query() {
    val manifestGuesses = (endedNames + retainedSnapshots).flatMap { listOf("$it-m1", "$it-m2", "$it-m3", "$it-m4") }
    when (weighted("snapshots" to 3, "manifests" to 3, "entries" to 4, "ls" to 2)) {
        "snapshots" -> emit(spaced("SNAPSHOTS"))
        "manifests" -> emit(spaced("MANIFESTS ${snapshotChoice()}"))
        "entries" -> emit(spaced("ENTRIES ${if (manifestGuesses.isEmpty()) "a-m1" else pick(manifestGuesses)}"))
        else -> emit(spaced("LS"))
    }
}

fun stepSmall(transaction: TransactionModel) {
    val removable = removableAtStart(transaction)
    val compactable = removable.groupBy { it.partition }.values.toList()
    when (weighted("append" to 5, "remove" to if (removable.isEmpty()) 0 else 3, "compact" to if (compactable.isEmpty()) 0 else 3, "bad" to 2)) {
        "append" -> appendRandomFile(transaction)
        "remove" -> removeFile(transaction, pick(removable))
        "compact" -> {
            val group = pick(compactable).shuffled(random)
            compactFiles(transaction, group.take(1 + random.nextInt(minOf(group.size, 3))))
        }
        else -> badTransactionLine(transaction)
    }
    transaction.steps--
}

fun badTransactionLine(transaction: TransactionModel) {
    val name = transaction.name
    val removable = removableAtStart(transaction)
    val startNames = removable.map { it.name }.toSet() + transaction.removed.keys
    val notLive = listOfNotNull(
        transaction.written.randomOrNull(random)?.name,
        transaction.removed.keys.randomOrNull(random),
        committedFiles.filter { it.name !in startNames && it.removedAt == Int.MAX_VALUE }.randomOrNull(random)?.name,
        committedFiles.filter { it.removedAt != Int.MAX_VALUE && it.name !in startNames }.randomOrNull(random)?.name,
        "nosuch",
    )
    val partitionGroups = removable.groupBy { it.partition }.values.toList()
    val mixedPair: List<FileModel>? =
        if (partitionGroups.size >= 2) listOf(partitionGroups[0].first(), partitionGroups[1].first()).shuffled(random) else null
    when (weighted("removeNotLive" to 3, "compactNotLive" to 3, "compactExists" to 2, "compactMixed" to if (mixedPair == null) 0 else 3, "compactShape" to 2, "abortShape" to 1)) {
        "removeNotLive" -> emit(spaced("REMOVE $name ${pick(notLive)}"))
        "compactNotLive" -> {
            val good = removable.shuffled(random).take(random.nextInt(0, 2)).map { it.name }
            emit(spaced("COMPACT $name ${freshFileName()} ${(good + pick(notLive)).distinct().joinToString(" ")}"))
        }
        "compactExists" -> {
            val existing = if (writtenFileNames.isEmpty()) "f1" else pick(writtenFileNames.toList())
            val replaced = removable.randomOrNull(random)?.name ?: "nosuch"
            emit(spaced("COMPACT $name $existing $replaced"))
        }
        "compactMixed" -> emit(spaced("COMPACT $name ${freshFileName()} ${mixedPair!!.joinToString(" ") { it.name }}"))
        "compactShape" -> {
            val some = removable.randomOrNull(random)?.name ?: "f1"
            emit(pick(listOf("COMPACT $name g9", "COMPACT $name", "COMPACT $name g9 $some $some", "COMPACT $name G9 $some", "COMPACT $name g9 -")))
        }
        else -> emit(pick(listOf("ABORT", "ABORT $name $name", "ABORT ${name.uppercase()}")))
    }
}

fun badExpire() {
    val current = retainedSnapshots.lastOrNull() ?: "a"
    val protected = protectedSnapshots().toList()
    val valid = expirable()
    val unknown = pick(expiredSnapshots.ifEmpty { NEVER_BEGUN } + NEVER_BEGUN)
    emit(
        weighted(
            "EXPIRE" to 1,
            "EXPIRE $current $current" to 1,
            "EXPIRE A" to 1,
            "EXPIRE -" to 1,
            "EXPIRE $current" to 3,
            "EXPIRE ${if (protected.isEmpty()) current else pick(protected)}" to 2,
            "EXPIRE $unknown" to 3,
            "EXPIRE ${(valid.take(1) + unknown).joinToString(" ")}" to 2,
            "EXPIRE ${(valid.take(1) + current).joinToString(" ")}" to 2,
            "ORPHANS now" to 1,
            "ORPHANS -" to 1,
        ),
    )
}

fun staleLine() {
    val name = if (endedNames.isNotEmpty() && chance(0.7)) pick(endedNames) else pick(NEVER_BEGUN)
    emit(
        weighted(
            "APPEND $name ${freshFileName()} ${randomRow().joinToString(",")}" to 2,
            "REMOVE $name ${writtenFileNames.firstOrNull() ?: "f1"}" to 1,
            "COMPACT $name ${freshFileName()} ${writtenFileNames.firstOrNull() ?: "f1"}" to 2,
            "COMMIT $name" to 2,
            "ABORT $name" to 2,
            "BEGIN ${if (endedNames.isEmpty()) name else pick(endedNames)}" to if (endedNames.isEmpty()) 0 else 2,
        ),
    )
}

val UNKNOWN_COMMANDS = listOf("abort", "Expire", "ORPHAN", "COMPACTION", "ROLLBACK", "VACUUM", "DELETE", "begin")

fun noise() {
    emit(
        weighted(
            "" to 2,
            "   " to 1,
            pick(UNKNOWN_COMMANDS) to 3,
            "${pick(UNKNOWN_COMMANDS)} ${active.firstOrNull()?.name ?: "a"}" to 2,
            "ORPHANS\t" to 1,
            "EXPIRE\ta" to 1,
            "CREATE x:long" to 1,
        ),
    )
}

fun preamble() {
    repeat(random.nextInt(0, 4)) {
        emit(pick(listOf("ORPHANS", "EXPIRE a", "ABORT a", "COMPACT a g1 f1", "LS", "BEGIN a", "abort a", "EXPIRE")))
    }
}

fun runNormal() {
    preamble()
    emit(spaced(schema.createLine))
    var begun = 0
    while (begun < options.transactions || active.isNotEmpty()) {
        val canBegin = begun < options.transactions && active.size < options.active
        val action = weighted(
            "begin" to if (canBegin) (if (active.isEmpty()) 100 else 5) else 0,
            "step" to if (active.isEmpty()) 0 else 12,
            "finish" to if (active.isEmpty()) 0 else 4,
            "expire" to if (expirable().isEmpty()) 0 else 2,
            "badExpire" to 2,
            "orphans" to 2,
            "scan" to 1 + options.scans / 5,
            "query" to 4,
            "stale" to 2,
            "noise" to 1,
        )
        when (action) {
            "begin" -> {
                begin(Plan.MIXED, steps = 1 + random.nextInt(options.files + 2))
                begun++
            }
            "step" -> pick(active).let { transaction -> if (transaction.steps > 0) stepSmall(transaction) else finish(transaction) }
            "finish" -> pick(active).let { transaction ->
                if (transaction.steps <= 0 || (!transaction.isEmpty && chance(0.25))) finish(transaction)
            }
            "expire" -> expirable().let { candidates -> expire(candidates.shuffled(random).take(1 + random.nextInt(minOf(2, candidates.size)))) }
            "badExpire" -> badExpire()
            "orphans" -> emit(spaced("ORPHANS"))
            "scan" -> scan()
            "query" -> query()
            "stale" -> staleLine()
            else -> noise()
        }
    }
    emit("SNAPSHOTS")
    emit("ORPHANS")
    emit("LS")
}

fun finish(transaction: TransactionModel) {
    if (chance(if (transaction.isEmpty) 0.8 else 0.15)) abort(transaction) else commit(transaction)
}

val LARGE_WORDS = listOf("ann", "bob", "cat", "dan", "eve", "fay", "gus", "hal")
val idsByBucket: Map<Int, List<Long>> by lazy { (1L..200_000L).groupBy { bucketHash(it.toString()) % 8 } }
val committedInOrder = ArrayDeque<FileModel>()
val OLD_FILE_WINDOW = 3_000
var pendingScans = 0.0

val bandStarts = HashMap<String, Int>()

fun largeRows(transaction: TransactionModel, bucket: Int): List<List<String>> {
    val ids = idsByBucket.getValue(bucket)
    val key = "${transaction.name}/$bucket"
    val start = bandStarts.getOrPut(key) { random.nextInt(ids.size - 8) }
    bandStarts[key] = (start + 8) % (ids.size - 8)
    return List(1 + random.nextInt(options.rows)) { listOf(ids[start + random.nextInt(8)].toString(), pick(LARGE_WORDS)) }
}

val filesByOrigin = HashMap<String, List<FileModel>>()

fun isRemovable(file: FileModel, transaction: TransactionModel): Boolean =
    file.isLiveAt(transaction.startSequence) && file.name !in transaction.removed

fun oldRemovableFile(transaction: TransactionModel, partition: String? = null): FileModel? {
    while (committedInOrder.isNotEmpty() && committedInOrder.first().removedAt != Int.MAX_VALUE) committedInOrder.removeFirst()
    if (committedInOrder.isEmpty()) return null
    repeat(40) {
        val candidate = committedInOrder[random.nextInt(minOf(OLD_FILE_WINDOW, committedInOrder.size))]
        if (isRemovable(candidate, transaction) && (partition == null || candidate.partition == partition)) return candidate
    }
    return null
}

fun sharedRemovableFile(transaction: TransactionModel): FileModel? = active
    .filter { it !== transaction }
    .flatMap { it.removed.values }
    .filter { isRemovable(it, transaction) }
    .randomOrNull(random)

fun stepLarge(transaction: TransactionModel) {
    when (transaction.plan) {
        Plan.APPEND -> appendFile(transaction, largeRows(transaction, pick(transaction.buckets)))
        Plan.REMOVE -> {
            val target = (if (chance(0.03)) sharedRemovableFile(transaction) else null) ?: oldRemovableFile(transaction)
            if (target != null) removeFile(transaction, target)
        }
        else -> {
            val bucket = pick(transaction.buckets).toString()
            val first = (if (chance(0.1)) sharedRemovableFile(transaction) else null)
                ?: oldRemovableFile(transaction, bucket)
                ?: oldRemovableFile(transaction)
            if (first != null) {
                val neighbors = filesByOrigin[first.origin].orEmpty()
                    .filter { it !== first && it.partition == first.partition && isRemovable(it, transaction) }
                    .shuffled(random)
                    .take(3 + random.nextInt(10))
                compactFiles(transaction, listOf(first) + neighbors)
            }
        }
    }
    transaction.steps--
}

fun afterCommitMaintenance() {
    if (commits % 30 == 0) {
        val candidates = expirable()
        val excess = retainedSnapshots.size - 200
        if (excess > 0 && candidates.isNotEmpty()) expire(candidates.take(minOf(excess, candidates.size)))
    }
    if (commits % 60 == 0) emit("ORPHANS")
}

fun largeScan() {
    val target = if (retainedSnapshots.size > 1 && chance(0.5)) "AT ${pick(retainedSnapshots)} " else ""
    val ids = idsByBucket.getValue(random.nextInt(8))
    val id = ids[random.nextInt(ids.size)]
    val condition = if (chance(0.9)) "id=$id" else "id>=$id AND id<=${id + random.nextInt(1, 6)}"
    emit("SCAN ${target}WHERE $condition")
}

fun endLarge(transaction: TransactionModel) {
    if (transaction.isEmpty || chance(0.05)) {
        abort(transaction)
    } else {
        val before = commits
        commit(transaction)
        if (commits > before) {
            committedInOrder.addAll(transaction.written)
            filesByOrigin[transaction.name] = transaction.written.toList()
            afterCommitMaintenance()
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
    var targetActive = 1
    while (begun < options.transactions || active.isNotEmpty()) {
        if (chance(0.01)) targetActive = 1 + random.nextInt(options.active)
        val canBegin = begun < options.transactions && active.size < targetActive
        if (active.isEmpty() || (canBegin && chance(0.02))) {
            val plan = if (liveFileNames.size < 500) Plan.APPEND else weighted(Plan.APPEND to 50, Plan.REMOVE to 30, Plan.COMPACT to 20)
            val steps = when (plan) {
                Plan.APPEND -> options.files - 8 + random.nextInt(17)
                Plan.REMOVE -> 12 + random.nextInt(18)
                else -> 2 + random.nextInt(3)
            }
            val bucket = random.nextInt(8)
            begin(plan, steps, if (chance(0.8)) listOf(bucket) else listOf(bucket / 2 * 2, bucket / 2 * 2 + 1))
            begun++
            continue
        }
        val transaction = if (chance(0.85)) active.first() else pick(active)
        if (transaction.steps > 0) stepLarge(transaction) else endLarge(transaction)
    }
    emit("SNAPSHOTS")
}

if (largeScale) runLarge() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
