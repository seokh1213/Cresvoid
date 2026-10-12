import kotlin.random.Random

class Options(val transactions: Int, val keys: Int, val active: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--transactions" to "40", "--keys" to "12", "--active" to "5", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        transactions = values.getValue("--transactions").toInt(),
        keys = values.getValue("--keys").toInt(),
        active = values.getValue("--active").toInt(),
        seed = values.getValue("--seed").toLong(),
    ).also { require(it.active >= 1 && it.keys >= 1) { "--active 와 --keys 는 1 이상이다" } }
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.transactions >= 10_000
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

fun pad(line: String): String = if (!largeScale && chance(0.04)) "  " + line.replace(" ", pick(listOf("  ", "   "))) + " " else line

val CURATED_KEYS = listOf(
    "doc", "doc0", "doc10", "doc2", "k", "k1", "k10", "k2", "a", "b", "m", "07", "7", "0", "z", "a0", "zz", "mid", "x9",
    "abcdefghijklmnop", "alice", "bob", "b10", "b2",
)
val VALUES = listOf("0", "1", "7", "07", "on", "off", "100", "110", "x", "9z", "abcdefghijklmnop", "1000000000000000")
val SPECIAL_NAMES = listOf("a", "k1", "doc", "z9", "abcdefghijklmnop", "m0", "init", "q", "t")
val UNKNOWN_COMMANDS = listOf("begin", "Begin", "SELECT", "READS", "COMMITT", "ABORT", "GET", "PUT", "UPDATE", "read", "Scan", "END")

class TransactionModel(val name: String, var plannedOperations: Int) {
    val written = LinkedHashSet<String>()
}

val active = ArrayList<TransactionModel>()
val endedNames = ArrayList<String>()
val usedNames = HashSet<String>()
val activeWriterOf = HashMap<String, TransactionModel>()
val committedKeys = LinkedHashSet<String>()
val recentlyCommittedKeys = ArrayDeque<String>()
var nameCounter = 0

fun freshName(): String {
    if (!largeScale && chance(0.08)) {
        val special = SPECIAL_NAMES.filter { it !in usedNames }
        if (special.isNotEmpty()) return pick(special)
    }
    while (true) {
        val name = if (largeScale) "t${(nameCounter++).toString(36)}" else "t${nameCounter++}"
        if (name !in usedNames) return name
    }
}

fun begin(plannedOperations: Int, repeatableShare: Double = 0.5): TransactionModel {
    val name = freshName()
    usedNames += name
    val isolationText = when {
        chance(repeatableShare) -> " REPEATABLE READ"
        largeScale -> ""
        else -> pick(listOf("", "", " READ COMMITTED"))
    }
    emit(pad("BEGIN $name$isolationText"))
    return TransactionModel(name, plannedOperations).also { active += it }
}

fun end(transaction: TransactionModel, commit: Boolean) {
    emit(pad(if (commit) "COMMIT ${transaction.name}" else "ROLLBACK ${transaction.name}"))
    active -= transaction
    endedNames += transaction.name
    for (key in transaction.written) {
        activeWriterOf.remove(key)
        if (!commit) continue
        committedKeys += key
        recentlyCommittedKeys.addLast(key)
        if (recentlyCommittedKeys.size > 4) recentlyCommittedKeys.removeFirst()
    }
}

fun writableBy(transaction: TransactionModel, key: String): Boolean = activeWriterOf[key].let { it == null || it === transaction }

fun write(transaction: TransactionModel, key: String, value: String) {
    check(writableBy(transaction, key))
    emit(pad("WRITE ${transaction.name} $key $value"))
    transaction.written += key
    activeWriterOf[key] = transaction
}

fun read(transaction: TransactionModel, key: String) = emit(pad("READ ${transaction.name} $key"))

fun scan(transaction: TransactionModel, start: String, end: String) = emit(pad("SCAN ${transaction.name} $start $end"))

fun keyNear(key: String): String = weighted(
    { key } to 6,
    { (key + pick(listOf("0", "a", "z"))).take(16) } to 2,
    { key.dropLast(1).ifEmpty { key } } to 1,
    { pick(listOf("0", "00", "zzzzzzzzzzzzzzzz", "n", "c")) } to 1,
)()

val normalKeys: List<String> by lazy {
    val curated = CURATED_KEYS.shuffled(random).take(minOf(options.keys, CURATED_KEYS.size))
    curated + (curated.size until options.keys).map { "k$it" }
}

fun normalReadKey(transaction: TransactionModel): String = weighted(
    { if (transaction.written.isEmpty()) pick(normalKeys) else pick(transaction.written.toList()) } to 3,
    { if (committedKeys.isEmpty()) pick(normalKeys) else pick(committedKeys.toList()) } to 5,
    { pick(normalKeys) } to 3,
    { keyNear(pick(normalKeys)) } to 1,
)()

fun normalWrite(transaction: TransactionModel) {
    val recentlyCommitted = recentlyCommittedKeys.filter { writableBy(transaction, it) }
    val candidates = normalKeys.filter { writableBy(transaction, it) }
    val key = when {
        recentlyCommitted.isNotEmpty() && chance(0.4) -> pick(recentlyCommitted)
        transaction.written.isNotEmpty() && chance(0.25) -> pick(transaction.written.toList())
        candidates.isNotEmpty() -> pick(candidates)
        else -> return
    }
    write(transaction, key, pick(VALUES))
}

fun normalScan(transaction: TransactionModel) {
    val first = keyNear(pick(normalKeys))
    val second = if (chance(0.15)) pick(listOf("zzzzzzzzzzzzzzzz", "zz", "0")) else keyNear(pick(normalKeys))
    if (first == second) return
    scan(transaction, minOf(first, second), maxOf(first, second))
}

fun malformedLine(): String {
    val name = if (active.isNotEmpty() && chance(0.7)) pick(active).name else pick(listOf("t1", "t9", "zz"))
    val key = if (largeScale) scaleKey() else pick(normalKeys)
    return weighted(
        { "${pick(UNKNOWN_COMMANDS)} $name $key" } to 4,
        { pick(UNKNOWN_COMMANDS) } to 1,
        { "READ\t$name $key" } to 1,
        { "\t" } to 1,
        { "BEGIN" } to 1,
        { "BEGIN ${pick(listOf("1t", "T1", "t-1", "abcdefghijklmnopq", "tx_1", "é"))}" } to 2,
        { "BEGIN $name ${pick(listOf("READ UNCOMMITTED", "REPEATABLE", "READ", "COMMITTED", "repeatable read", "READ COMMITTED NOW", "SERIALIZABLE READ", "serializable", "Serializable"))}" } to 3,
        { "READ $name" } to 1,
        { "READ $name $key extra" } to 1,
        { "READ $name ${pick(listOf("K", "k-1", "abcdefghijklmnopq", "", "k\r", "키"))}" } to 2,
        { "READ $name $key ${pick(listOf("FOR DELETE", "for update", "FOR share", "FOR"))}" } to 2,
        { "WRITE $name $key" } to 1,
        { "WRITE $name $key ${pick(listOf("Big", "v.1", "12345678901234567", "-1"))}" } to 2,
        { "WRITE $name $key 1 2" } to 1,
        { "SCAN $name $key $key" } to 2,
        { "SCAN $name zz a" } to 1,
        { "SCAN $name $key" } to 1,
        { "SCAN $name a b c" } to 1,
        { "SCAN $name a b FOR share" } to 1,
        { "COMMIT" } to 1,
        { "COMMIT $name now" } to 1,
        { "ROLLBACK $name $name" } to 1,
        { "COMMIT T1" } to 1,
    )()
}

val NEVER_BEGUN_NAMES = listOf("ghost", "nobody", "u1", "zz")

fun staleLine() {
    val ended = endedNames.isNotEmpty() && chance(0.75)
    val name = if (ended) pick(endedNames) else pick(NEVER_BEGUN_NAMES)
    val key = if (largeScale) scaleKey() else pick(normalKeys)
    emit(
        weighted(
            "READ $name $key" to 3,
            "WRITE $name $key 1" to 2,
            "SCAN $name a zz" to 1,
            "COMMIT $name" to 2,
            "ROLLBACK $name" to 2,
            "BEGIN $name" to if (ended) 3 else 0,
        ),
    )
}

fun initialLoad(keys: List<String>) {
    val loader = begin(plannedOperations = 0, repeatableShare = if (largeScale) 0.0 else 0.3)
    keys.forEach { write(loader, it, if (largeScale) "${random.nextInt(100)}" else pick(VALUES)) }
    end(loader, commit = true)
}

fun runNormal() {
    if (chance(0.8)) initialLoad(normalKeys.shuffled(random).take(random.nextInt(1, normalKeys.size + 1)))
    var begun = 0
    val leaveActiveAtEnd = chance(0.3)
    while (begun < options.transactions || (active.isNotEmpty() && !leaveActiveAtEnd)) {
        val canBegin = begun < options.transactions && active.size < options.active
        val action = weighted(
            "begin" to if (canBegin) (if (active.isEmpty()) 100 else 5) else 0,
            "read" to if (active.isEmpty()) 0 else 8,
            "write" to if (active.isEmpty()) 0 else 7,
            "scan" to if (active.isEmpty()) 0 else 4,
            "commit" to if (active.isEmpty()) 0 else 3,
            "rollback" to if (active.isEmpty()) 0 else 1,
            "malformed" to 2,
            "stale" to 1,
            "blank" to 1,
        )
        when (action) {
            "begin" -> {
                begin(plannedOperations = 0, repeatableShare = 0.5)
                begun++
            }
            "read" -> pick(active).let { read(it, normalReadKey(it)) }
            "write" -> normalWrite(pick(active))
            "scan" -> normalScan(pick(active))
            "commit" -> end(pick(active), commit = true)
            "rollback" -> end(pick(active), commit = false)
            "malformed" -> emit(malformedLine())
            "stale" -> staleLine()
            else -> emit(pick(listOf("", " ", "   ")))
        }
        if (begun >= options.transactions && leaveActiveAtEnd && chance(0.2)) break
    }
}

val sortedLoadedKeys: List<String> by lazy { (0 until options.keys).map { "k$it" }.sorted() }
val hotKeys: List<String> by lazy { (0 until 200).map { sortedLoadedKeys[random.nextInt(sortedLoadedKeys.size)] }.distinct() }
var freshKeyCounter = 0

fun scaleKey(): String = when {
    chance(0.1) -> pick(hotKeys)
    else -> sortedLoadedKeys[random.nextInt(sortedLoadedKeys.size)]
}

fun scaleWrite(transaction: TransactionModel) {
    repeat(4) {
        val key = when {
            chance(0.03) -> "n${(freshKeyCounter++).toString(36)}"
            transaction.written.isNotEmpty() && chance(0.1) -> pick(transaction.written.toList())
            else -> scaleKey()
        }
        if (writableBy(transaction, key)) {
            write(transaction, key, "${random.nextInt(100)}")
            return
        }
    }
}

fun scaleScan(transaction: TransactionModel) {
    val startIndex = random.nextInt(sortedLoadedKeys.size)
    val end = sortedLoadedKeys.getOrNull(startIndex + 2 + random.nextInt(5)) ?: "zzzzzzzzzzzzzzzz"
    scan(transaction, sortedLoadedKeys[startIndex], end)
}

fun scaleOperation(transaction: TransactionModel) {
    when (weighted("read" to 36, "write" to 32, "scan" to 32)) {
        "read" -> read(transaction, if (transaction.written.isNotEmpty() && chance(0.15)) pick(transaction.written.toList()) else scaleKey())
        "write" -> scaleWrite(transaction)
        else -> scaleScan(transaction)
    }
    transaction.plannedOperations--
}

fun runScale() {
    initialLoad(sortedLoadedKeys.shuffled(random))
    var begun = 0
    while (begun < options.transactions || active.isNotEmpty()) {
        if (begun < options.transactions && active.size < options.active) {
            begin(plannedOperations = 2 + random.nextInt(7), repeatableShare = 0.5)
            begun++
            continue
        }
        when {
            chance(0.002) -> emit(malformedLine())
            chance(0.002) -> staleLine()
        }
        val transaction = pick(active)
        if (transaction.plannedOperations > 0) scaleOperation(transaction) else end(transaction, commit = !chance(0.1))
    }
}

if (largeScale) runScale() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
