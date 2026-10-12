import java.time.LocalDate
import kotlin.random.Random

class Options(val transactions: Int, val files: Int, val rows: Int, val scans: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--transactions" to "30",
        "--files" to "3",
        "--rows" to "3",
        "--scans" to "2",
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
        scans = values.getValue("--scans").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.transactions >= 1 && options.files >= 1 && options.rows >= 1 && options.scans >= 0) {
        "--transactions, --files, --rows 는 1 이상이고 --scans 는 0 이상이다"
    }
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

val LARGE_SCHEMA = schemaOf("ts:timestamp id:long kind:string", "day(ts) bucket[16](id)")
val SCHEMAS = listOf(
    schemaOf("ts:timestamp id:long kind:string", "day(ts) bucket[4](id)") to 6,
    schemaOf("id:long name:string") to 3,
    schemaOf("name:string score:long", "bucket[3](name)") to 2,
    schemaOf("ts:timestamp v:long", "day(ts)") to 2,
    schemaOf("a:long b:string c:timestamp d:long", "bucket[7](d) day(c) bucket[2](b)") to 2,
    schemaOf("id:long ts:timestamp", "bucket[1](id) day(ts)") to 1,
    schemaOf("k:string", "bucket[999](k)") to 1,
    schemaOf(
        "c1:long c2:long c3:long c4:string c5:string c6:timestamp c7:timestamp abcdefghijklmnop:long",
        "day(c7) bucket[16](c1)",
    ) to 1,
)
val schema = if (largeScale) LARGE_SCHEMA else weighted(*SCHEMAS.toTypedArray())

fun bucketHash(text: String): Int = text.foldIndexed(0) { index, hash, char -> hash + (index + 1) * char.code }

fun partitionOf(row: List<String>): String = schema.fields.joinToString(",") { field ->
    when (field) {
        is DayField -> row[field.source].substring(0, 10)
        is BucketField -> (bucketHash(row[field.source]) % field.buckets).toString()
    }
}

val COMMON_WORDS = listOf("click", "view", "buy", "cart", "home")
val RARE_WORDS = listOf("0", "007", "a", "z9", "10", "abcdefghijklmnop", "zzzzzzzzzzzzzzzz", "x1y2z3")
val COMMON_DAYS = listOf("2024-03-01", "2024-03-02", "2024-03-03")
val RARE_DAYS = listOf("2024-02-29", "2000-02-29", "1970-01-01", "2023-12-31", "9999-12-31", "2024-04-30")
val EDGE_TIMES = listOf("00:00:00", "23:59:59", "00:00:01", "23:59:58", "12:00:00")
val RARE_LONGS = listOf("0", "-1", "999999999999999999", "-999999999999999999", "100000000000000000")

fun twoDigits(number: Int): String = number.toString().padStart(2, '0')

fun clock(secondOfDay: Int): String =
    "${twoDigits(secondOfDay / 3_600)}:${twoDigits(secondOfDay / 60 % 60)}:${twoDigits(secondOfDay % 60)}"

fun longValue(): String = weighted(
    { random.nextInt(0, 30).toString() } to 12,
    { (-random.nextInt(1, 20)).toString() } to 2,
    { random.nextLong(1_000, 1_000_000_000_000L).toString() } to 1,
    { pick(RARE_LONGS) } to 1,
)()

fun stringValue(): String = if (chance(0.85)) pick(COMMON_WORDS) else pick(RARE_WORDS)

fun dayValue(): String = if (chance(0.85)) pick(COMMON_DAYS) else pick(RARE_DAYS)

fun timeValue(): String = if (chance(0.2)) pick(EDGE_TIMES) else clock(random.nextInt(86_400))

fun timestampValue(day: String = dayValue()): String = "${day}T${timeValue()}"

fun valueOf(type: Type): String = when (type) {
    Type.LONG -> longValue()
    Type.STRING -> stringValue()
    Type.TIMESTAMP -> timestampValue()
}

fun randomRow(): List<String> = schema.columns.map { valueOf(it.type) }

fun sameBucketValue(anchorValue: String, type: Type, buckets: Int): String {
    val target = bucketHash(anchorValue) % buckets
    repeat(200) {
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

fun rowText(row: List<String>): String = row.joinToString(",")

class FileModel(val name: String, val rows: List<List<String>>)

class TransactionModel(val name: String, val baseCommit: Int, val startLive: List<FileModel>, var budget: Int) {
    val appended = mutableListOf<FileModel>()
    val removed = linkedSetOf<String>()
    val isEmpty: Boolean get() = appended.isEmpty() && removed.isEmpty()
}

val active = mutableListOf<TransactionModel>()
val stranded = mutableListOf<TransactionModel>()
val begunNames = linkedSetOf<String>()
val committedNames = mutableListOf<String>()
val liveFiles = LinkedHashMap<String, FileModel>()
val writtenFileNames = linkedSetOf<String>()
val knownRows = mutableListOf<List<String>>()
var commitCount = 0
var fileCounter = 0
var transactionCounter = 0

val SPECIAL_TRANSACTIONS = listOf("a", "b", "x9", "abcdefghijklmnop", "meta", "snap", "f1")
val SPECIAL_FILES = listOf("abcdefghijklmnop", "z", "f0", "data9", "a", "meta1", "snap")
val NEVER_BEGUN = listOf("ghost", "nobody", "q0")

fun freshTransactionName(): String {
    if (chance(0.1)) SPECIAL_TRANSACTIONS.firstOrNull { it !in begunNames }?.let { return it }
    while (true) {
        val name = "t${++transactionCounter}"
        if (name !in begunNames) return name
    }
}

fun freshFileName(): String {
    if (chance(0.08)) SPECIAL_FILES.firstOrNull { it !in writtenFileNames }?.let { return it }
    while (true) {
        val name = "f${++fileCounter}"
        if (name !in writtenFileNames) return name
    }
}

fun begin(budget: Int): TransactionModel {
    val name = freshTransactionName()
    begunNames += name
    emit(spaced("BEGIN $name"))
    return TransactionModel(name, commitCount, liveFiles.values.toList(), budget).also { active += it }
}

fun append(transaction: TransactionModel) {
    val name = freshFileName()
    val anchor = randomRow()
    val rows = listOf(anchor) + List(random.nextInt(0, options.rows)) { rowLike(anchor) }
    emit(spaced("APPEND ${transaction.name} $name ${rows.joinToString(" ", transform = ::rowText)}"))
    writtenFileNames += name
    knownRows += rows
    transaction.appended += FileModel(name, rows)
}

fun mixedRows(): List<List<String>>? {
    if (schema.fields.isEmpty()) return null
    val anchor = randomRow()
    repeat(50) {
        val other = randomRow()
        if (partitionOf(other) != partitionOf(anchor)) return listOf(anchor, other).shuffled(random)
    }
    return null
}

fun badValue(type: Type): String = when (type) {
    Type.LONG -> pick(listOf("07", "-0", "1.5", "+3", "1e3", "1234567890123456789", "x", "--1", "0x1f"))
    Type.STRING -> pick(listOf("Click", "a-b", "abcdefghijklmnopq", "a_b", "가", "A"))
    Type.TIMESTAMP -> pick(
        listOf(
            "2023-02-29T10:00:00", "2024-03-01T24:00:00", "2024-03-01T10:00", "2024-13-01T00:00:00",
            "1969-12-31T23:59:59", "2024-04-31T00:00:00", "2100-02-29T00:00:00", "2024-3-01T00:00:00",
            "2024-03-01t10:00:00", "2024-03-01T10:60:00", "2024-03-01T10:00:60", "2024-00-10T00:00:00",
        ),
    )
}

fun badRowText(): String {
    val row = randomRow().toMutableList()
    return weighted(
        {
            val column = random.nextInt(row.size)
            row[column] = badValue(schema.columns[column].type)
            rowText(row)
        } to 6,
        { rowText(row + valueOf(Type.LONG)) } to 1,
        { if (row.size > 1) rowText(row.dropLast(1)) else "${row[0]},${row[0]}" } to 1,
        { if (row.size > 1) rowText(row).replaceFirst(",", ",,") else "," } to 1,
        { rowText(row) + "," } to 1,
        { rowText(row).replace(",", ";") + ";" } to 1,
    )()
}

fun badAppend(transaction: TransactionModel) {
    val name = transaction.name
    when (weighted("exists" to 3, "mixed" to 3, "row" to 5, "shape" to 2, "existsMixed" to 1)) {
        "exists" -> if (writtenFileNames.isNotEmpty()) {
            emit(spaced("APPEND $name ${pick(writtenFileNames.toList())} ${rowText(randomRow())}"))
        }
        "mixed" -> mixedRows()?.let { rows -> emit(spaced("APPEND $name ${freshFileName()} ${rows.joinToString(" ", transform = ::rowText)}")) }
        "row" -> emit(spaced("APPEND $name ${freshFileName()} ${rowText(randomRow())} ${badRowText()}"))
        "shape" -> emit(
            pick(
                listOf(
                    "APPEND $name ${freshFileName()}",
                    "APPEND $name",
                    "APPEND $name F1 ${rowText(randomRow())}",
                    "APPEND $name f-1 ${rowText(randomRow())}",
                    "APPEND ${name.uppercase()} f9 ${rowText(randomRow())}",
                    "APPEND $name abcdefghijklmnopq ${rowText(randomRow())}",
                ),
            ),
        )
        else -> if (writtenFileNames.isNotEmpty()) {
            mixedRows()?.let { rows -> emit("APPEND $name ${pick(writtenFileNames.toList())} ${rows.joinToString(" ", transform = ::rowText)}") }
        }
    }
}

fun remove(transaction: TransactionModel) {
    val candidates = transaction.startLive.filter { it.name !in transaction.removed }
    if (candidates.isEmpty()) return badRemove(transaction)
    val file = pick(candidates)
    emit(spaced("REMOVE ${transaction.name} ${file.name}"))
    transaction.removed += file.name
}

fun badRemove(transaction: TransactionModel) {
    val startNames = transaction.startLive.map { it.name }.toSet()
    val laterFiles = liveFiles.keys.filter { it !in startNames }
    val otherWritten = writtenFileNames.filter { it !in startNames }
    val choices = listOfNotNull(
        transaction.appended.randomOrNull(random)?.name,
        transaction.removed.toList().randomOrNull(random),
        laterFiles.randomOrNull(random),
        otherWritten.randomOrNull(random),
        pick(listOf("nosuch", "f999", "zz")),
    )
    when (weighted("state" to 8, "shape" to 2)) {
        "state" -> emit(spaced("REMOVE ${transaction.name} ${pick(choices)}"))
        else -> emit(pick(listOf("REMOVE ${transaction.name}", "REMOVE ${transaction.name} f1 f2", "REMOVE ${transaction.name} F1", "REMOVE ${transaction.name} -")))
    }
}

fun commit(transaction: TransactionModel) {
    if (transaction.isEmpty) {
        emit(spaced("COMMIT ${transaction.name}"))
        return
    }
    active -= transaction
    if (transaction.baseCommit != commitCount) {
        stranded += transaction
        return
    }
    emit(spaced("COMMIT ${transaction.name}"))
    transaction.removed.forEach(liveFiles::remove)
    transaction.appended.forEach { liveFiles[it.name] = it }
    commitCount++
    committedNames += transaction.name
}

fun knownValue(column: Int): String =
    if (knownRows.isNotEmpty() && chance(0.7)) pick(knownRows)[column] else valueOf(schema.columns[column].type)

fun conditionValue(column: Int): String {
    val type = schema.columns[column].type
    if (type != Type.TIMESTAMP || chance(0.5)) return knownValue(column)
    val day = knownValue(column).substring(0, 10)
    return "${day}T${pick(EDGE_TIMES)}"
}

fun condition(): String {
    val sourceColumns = schema.fields.map { it.source }
    val column = if (sourceColumns.isNotEmpty() && chance(0.6)) pick(sourceColumns) else random.nextInt(schema.columns.size)
    val operator = weighted("=" to 3, "<" to 2, "<=" to 2, ">" to 2, ">=" to 2)
    return "${schema.columns[column].name}$operator${conditionValue(column)}"
}

fun scanTarget(): String = when {
    committedNames.isNotEmpty() && chance(0.4) -> "AT ${pick(committedNames)} "
    chance(0.05) -> "AT ${pick(NEVER_BEGUN + active.map { it.name })} "
    else -> ""
}

fun scan() {
    val conditions = List(weighted(0 to 2, 1 to 4, 2 to 3, 3 to 1)) { condition() }
    val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
    emit(spaced("SCAN ${scanTarget()}$where".trimEnd()))
}

fun badScan() {
    val column = schema.columns.first()
    val good = "${column.name}=${valueOf(column.type)}"
    emit(
        weighted(
            "SCAN WHERE" to 2,
            "SCAN WHERE $good AND" to 2,
            "SCAN WHERE AND $good" to 1,
            "SCAN WHERE $good OR $good" to 1,
            "SCAN WHERE $good and $good" to 1,
            "SCAN WHERE $good $good" to 1,
            "SCAN AT" to 1,
            "SCAN AT a b" to 1,
            "SCAN WHERE $good AT a" to 1,
            "SCAN AT A" to 1,
            "SCAN AT -" to 1,
            "SCAN $good" to 1,
            "SCAN WHERE nosuchcolumn=1" to 1,
            "SCAN WHERE ${column.name}==${valueOf(column.type)}" to 1,
            "SCAN WHERE ${column.name}=>${valueOf(column.type)}" to 1,
            "SCAN WHERE ${column.name}<>${valueOf(column.type)}" to 1,
            "SCAN WHERE ${column.name}!=${valueOf(column.type)}" to 1,
            "SCAN WHERE ${column.name}=" to 1,
            "SCAN WHERE =${valueOf(column.type)}" to 1,
            "SCAN WHERE ${column.name}" to 1,
            "SCAN WHERE ${column.name.uppercase()}=${valueOf(column.type)}" to 1,
            "SCAN WHERE ${column.name}=${badValue(column.type)}" to 2,
            "SCAN where $good" to 1,
            "SCAN AT ${committedNames.lastOrNull() ?: "a"} WHERE" to 1,
        ),
    )
}

fun query() {
    val snapshotNames = committedNames + active.map { it.name } + NEVER_BEGUN
    val manifestGuesses = committedNames.flatMap { listOf("$it-m1", "$it-m2", "$it-m3") }
    when (weighted("snapshots" to 3, "manifests" to 4, "entries" to 4, "ls" to 1, "bad" to 2)) {
        "snapshots" -> emit(spaced("SNAPSHOTS"))
        "manifests" -> emit(spaced("MANIFESTS ${if (committedNames.isNotEmpty() && chance(0.75)) pick(committedNames) else pick(snapshotNames)}"))
        "entries" -> {
            val others = listOf("meta-1", "snap-${committedNames.lastOrNull() ?: "a"}-1", "Zz", "-", "a-m0") + writtenFileNames.take(3)
            emit(spaced("ENTRIES ${if (manifestGuesses.isNotEmpty() && chance(0.75)) pick(manifestGuesses) else pick(others)}"))
        }
        "ls" -> emit(spaced("LS"))
        else -> emit(
            pick(
                listOf("SNAPSHOTS now", "LS -l", "MANIFESTS", "MANIFESTS -", "MANIFESTS A", "MANIFESTS a b", "ENTRIES", "ENTRIES a-m1 a-m2"),
            ),
        )
    }
}

fun staleLine() {
    val ended = committedNames + stranded.map { it.name }
    val name = if (committedNames.isNotEmpty() && chance(0.6)) pick(committedNames) else pick(NEVER_BEGUN)
    val row = rowText(randomRow())
    emit(
        weighted(
            "APPEND $name ${freshFileName()} $row" to 3,
            "REMOVE $name ${writtenFileNames.firstOrNull() ?: "f1"}" to 2,
            "COMMIT $name" to 3,
            "BEGIN ${if (ended.isNotEmpty()) pick(ended) else name}" to if (ended.isNotEmpty()) 3 else 0,
            "BEGIN ${active.firstOrNull()?.name ?: name}" to if (active.isNotEmpty()) 2 else 0,
        ),
    )
}

val UNKNOWN_COMMANDS = listOf("begin", "Scan", "SELECT", "INSERT", "DELETE", "ROLLBACK", "SNAPSHOT", "LIST", "COMMITS", "ls", "Create")

fun noise() {
    emit(
        weighted(
            "" to 2,
            " " to 1,
            "   " to 1,
            pick(UNKNOWN_COMMANDS) to 3,
            "${pick(UNKNOWN_COMMANDS)} ${active.firstOrNull()?.name ?: "a"}" to 2,
            "LS\t" to 1,
            "SNAPSHOTS\r" to 1,
            "\t" to 1,
            "BEGIN\ta" to 1,
            "BEGIN" to 1,
            "BEGIN T1" to 1,
            "BEGIN 1t" to 1,
            "BEGIN abcdefghijklmnopq" to 1,
            "BEGIN a b" to 1,
            "COMMIT" to 1,
            "COMMIT a b" to 1,
            "CREATE ${pick(listOf("x:long", "id:long name:string"))}" to 1,
        ),
    )
}

fun preamble() {
    repeat(random.nextInt(0, 4)) {
        emit(
            weighted(
                "LS" to 2,
                "SNAPSHOTS" to 1,
                "BEGIN a" to 2,
                "SCAN" to 1,
                "SCAN WHERE id=1" to 1,
                "COMMIT a" to 1,
                "ENTRIES a-m1" to 1,
                "MANIFESTS a" to 1,
                "begin a" to 1,
                "" to 1,
            ),
        )
    }
    repeat(random.nextInt(0, 4)) {
        emit(
            pick(
                listOf(
                    "CREATE", "CREATE id:long PARTITION", "CREATE PARTITION day(ts)", "CREATE id:int", "CREATE id:Long",
                    "CREATE Id:long", "CREATE id:long id:string", "CREATE 1d:long", "CREATE id", "CREATE id:long:x",
                    "CREATE ts:timestamp PARTITION day(id)", "CREATE id:long PARTITION day(id)",
                    "CREATE ts:timestamp PARTITION bucket[4](ts)", "CREATE id:long PARTITION bucket[0](id)",
                    "CREATE id:long PARTITION bucket[1000](id)", "CREATE id:long PARTITION bucket[04](id)",
                    "CREATE id:long PARTITION bucket(id)", "CREATE id:long PARTITION bucket[4]id",
                    "CREATE ts:timestamp PARTITION Day(ts)", "CREATE ts:timestamp PARTITION day(ts) day(ts)",
                    "CREATE id:long PARTITION bucket[4](id) bucket[8](id)",
                    "CREATE a:long b:long c:long d:long e:long f:long g:long h:long i:long",
                    "CREATE id:long PARTITION bucket[4](id) PARTITION bucket[4](id)",
                    "CREATE id:long partition bucket[4](id)", "CREATE id:long PARTITION bucket[4](id) x:long",
                    "CREATE abcdefghijklmnopq:long", "CREATE ts:timestamp PARTITION day(TS)",
                ),
            ),
        )
    }
}

fun runNormal() {
    preamble()
    emit(spaced(schema.createLine))
    if (chance(0.4)) emit(pick(listOf("CREATE x:long", schema.createLine, "CREATE")))
    var begun = 0
    while (begun < options.transactions || active.isNotEmpty()) {
        val canBegin = begun < options.transactions && active.size < 3
        val action = weighted(
            "begin" to if (canBegin) (if (active.isEmpty()) 100 else 2) else 0,
            "append" to if (active.isEmpty()) 0 else 10,
            "remove" to if (active.isEmpty()) 0 else 4,
            "finish" to if (active.isEmpty()) 0 else 3,
            "badAppend" to if (active.isEmpty()) 0 else 2,
            "badRemove" to if (active.isEmpty()) 0 else 2,
            "query" to 4,
            "scan" to 3 + options.scans * 2,
            "badScan" to 2,
            "stale" to 2,
            "noise" to 2,
            "stranded" to if (stranded.isEmpty()) 0 else 1,
        )
        when (action) {
            "begin" -> {
                begin(budget = 1 + random.nextInt(options.files + 2))
                begun++
            }
            "append" -> pick(active).let { transaction ->
                if (transaction.budget-- > 0) append(transaction) else commit(transaction)
            }
            "remove" -> remove(pick(active))
            "finish" -> pick(active).let { transaction -> if (transaction.budget <= 0 || chance(0.3)) commit(transaction) }
            "badAppend" -> badAppend(pick(active))
            "badRemove" -> badRemove(pick(active))
            "query" -> query()
            "scan" -> scan()
            "badScan" -> badScan()
            "stale" -> staleLine()
            "noise" -> noise()
            else -> pick(stranded).let { transaction ->
                if (chance(0.5)) append(transaction) else remove(transaction)
            }
        }
    }
    emit("SNAPSHOTS")
    repeat(2) { scan() }
    if (chance(0.5)) emit("LS")
}

val LARGE_KINDS = listOf("click", "view", "buy", "cart", "search", "home", "share")

fun runLarge() {
    emit(LARGE_SCHEMA.createLine)
    val idsByBucket = (1..40_000).map { it.toString() }.groupBy { bucketHash(it) % 16 }
    val firstDay = LocalDate.of(2020, 1, 1)
    val liveNames = ArrayList<String>()
    val idsByDay = ArrayList<List<String>>()
    for (index in 0 until options.transactions) {
        val transaction = "t${index.toString(36)}"
        val day = firstDay.plusDays(index.toLong()).toString()
        emit("BEGIN $transaction")
        val fileCount = maxOf(1, options.files - 6 + random.nextInt(13))
        val window = 86_400 / fileCount
        val dayIds = ArrayList<String>()
        val appended = ArrayList<String>()
        for (fileIndex in 0 until fileCount) {
            val pool = idsByBucket.getValue(random.nextInt(16))
            val rows = List(options.rows) {
                val id = pick(pool)
                dayIds += id
                "${day}T${clock(fileIndex * window + random.nextInt(window))},$id,${pick(LARGE_KINDS)}"
            }
            val file = "f${(fileCounter++).toString(36)}"
            emit("APPEND $transaction $file ${rows.joinToString(" ")}")
            appended += file
        }
        if (index % 5 == 4) {
            repeat(minOf(liveNames.size, 2 + random.nextInt(4))) {
                val position = random.nextInt(liveNames.size)
                emit("REMOVE $transaction ${liveNames[position]}")
                liveNames[position] = liveNames.last()
                liveNames.removeAt(liveNames.size - 1)
            }
        }
        emit("COMMIT $transaction")
        liveNames += appended
        idsByDay += dayIds
        repeat(options.scans) { emit(largeScan(index, firstDay, idsByDay, idsByBucket)) }
        if (index % 300 == 299) {
            emit("MANIFESTS t${random.nextInt(index + 1).toString(36)}")
            emit("ENTRIES t${random.nextInt(index + 1).toString(36)}-m1")
        }
    }
    emit("SNAPSHOTS")
}

fun largeScan(lastIndex: Int, firstDay: LocalDate, idsByDay: List<List<String>>, idsByBucket: Map<Int, List<String>>): String {
    val readsPast = chance(0.5)
    val snapshotIndex = if (readsPast) random.nextInt(lastIndex + 1) else lastIndex
    val dayIndex = if (chance(0.7)) snapshotIndex - random.nextInt(minOf(snapshotIndex + 1, 30)) else random.nextInt(snapshotIndex + 1)
    val day = firstDay.plusDays(dayIndex.toLong()).toString()
    val start = random.nextInt(86_400 - 600)
    val end = minOf(86_399, start + 600 + random.nextInt(4_800))
    val lower = if (chance(0.8)) "ts>=${day}T${clock(start)}" else "ts>${day}T${clock(start)}"
    val upper = if (chance(0.8)) "ts<${day}T${clock(end)}" else "ts<=${day}T${clock(end)}"
    val conditions = mutableListOf(lower, upper)
    if (chance(0.5)) {
        val id = if (chance(0.7)) pick(idsByDay[dayIndex]) else pick(idsByBucket.getValue(random.nextInt(16)))
        conditions.add(random.nextInt(conditions.size + 1), "id=$id")
    }
    val target = if (readsPast) "AT t${snapshotIndex.toString(36)} " else ""
    return "SCAN ${target}WHERE ${conditions.joinToString(" AND ")}"
}

if (largeScale) runLarge() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
