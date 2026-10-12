import kotlin.math.pow
import kotlin.random.Random

class Options(val transactions: Int, val keys: Int, val active: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--transactions" to "40", "--keys" to "6", "--active" to "5", "--seed" to "1")
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

val SERIALIZABLE = " SERIALIZABLE"
val REPEATABLE_READ = " REPEATABLE READ"

val CURATED_KEYS = listOf("a", "b", "c", "d", "k", "k1", "k10", "k2", "m", "x", "07", "7", "alice", "bob", "r10", "r10a")
val VALUES = listOf("0", "1", "2", "7", "07", "on", "off", "kim", "lee", "abcdefghijklmnop")
val SPECIAL_NAMES = listOf("a", "k", "alice", "z9", "abcdefghijklmnop", "m0", "init", "q", "t")
val NEVER_BEGUN_NAMES = listOf("ghost", "nobody", "u1", "zz")
val UNKNOWN_COMMANDS = listOf("begin", "Begin", "SELECT", "SERIALIZABLE", "LOCK", "ABORT", "read", "Commit", "END")

class TransactionModel(val name: String, var plannedOperations: Int = 0, val scans: Boolean = false)

val active = ArrayList<TransactionModel>()
val endedNames = ArrayList<String>()
val usedNames = HashSet<String>()
var nameCounter = 0
var episodeCounter = 0

fun freshName(): String {
    if (!largeScale && chance(0.06)) {
        val special = SPECIAL_NAMES.filter { it !in usedNames }
        if (special.isNotEmpty()) return pick(special)
    }
    while (true) {
        val name = if (largeScale) "t${(nameCounter++).toString(36)}" else "t${nameCounter++}"
        if (name !in usedNames) return name
    }
}

fun randomIsolation(): String = weighted(SERIALIZABLE to 6, REPEATABLE_READ to 2, "" to 1, " READ COMMITTED" to 1)

fun begin(isolation: String = randomIsolation(), plannedOperations: Int = 0, scans: Boolean = false, track: Boolean = true): TransactionModel {
    val name = freshName()
    usedNames += name
    emit(pad("BEGIN $name$isolation"))
    return TransactionModel(name, plannedOperations, scans).also { if (track) active += it }
}

fun finish(transaction: TransactionModel, commit: Boolean = true) {
    emit(pad(if (commit) "COMMIT ${transaction.name}" else "ROLLBACK ${transaction.name}"))
    active -= transaction
    endedNames += transaction.name
}

fun read(transaction: TransactionModel, key: String, lockClause: String = "") = emit(pad("READ ${transaction.name} $key$lockClause"))

fun write(transaction: TransactionModel, key: String, value: String = pick(VALUES)) = emit(pad("WRITE ${transaction.name} $key $value"))

fun scan(transaction: TransactionModel, start: String, end: String, lockClause: String = "") =
    emit(pad("SCAN ${transaction.name} $start $end$lockClause"))

fun malformedLine(): String {
    val name = if (active.isNotEmpty() && chance(0.7)) pick(active).name else pick(listOf("t1", "t9", "zz"))
    val key = pick(CURATED_KEYS)
    return weighted(
        { "${pick(UNKNOWN_COMMANDS)} $name $key" } to 2,
        { pick(UNKNOWN_COMMANDS) } to 1,
        { "BEGIN $name ${pick(listOf("SERIALIZABLE READ", "serializable", "Serializable", "SERIALIZABLE SERIALIZABLE", "READ SERIALIZABLE", "SERIALISABLE"))}" } to 5,
        { "BEGIN ${pick(listOf("T1", "1t", "abcdefghijklmnopq"))} SERIALIZABLE" } to 2,
        { "BEGIN $name READ UNCOMMITTED" } to 1,
        { "READ $name $key ${pick(listOf("FOR", "FOR DELETE", "for update"))}" } to 2,
        { "READ $name ${pick(listOf("K", "k\r"))}" } to 1,
        { "WRITE $name $key ${pick(listOf("Big", "12345678901234567"))}" } to 1,
        { "SCAN $name $key $key" } to 1,
        { "SCAN $name m a FOR SHARE" } to 1,
        { "COMMIT $name SERIALIZABLE" } to 1,
        { "COMMIT" } to 1,
    )()
}

fun staleLine(keys: List<String>) {
    val ended = endedNames.isNotEmpty() && chance(0.75)
    val name = if (ended) pick(endedNames) else pick(NEVER_BEGUN_NAMES)
    val key = pick(keys)
    emit(
        weighted(
            "READ $name $key" to 2,
            "WRITE $name $key 1" to 2,
            "SCAN $name a m" to 1,
            "COMMIT $name" to 2,
            "ROLLBACK $name" to 1,
            "BEGIN $name" to if (ended) 2 else 0,
            "BEGIN $name SERIALIZABLE" to if (ended) 2 else 0,
        ),
    )
}

val normalKeys: List<String> by lazy {
    val curated = CURATED_KEYS.shuffled(random).take(minOf(options.keys, CURATED_KEYS.size))
    curated + (curated.size until options.keys).map { "k$it" }
}

fun episodeKeys(count: Int): List<String> {
    val prefix = "y${episodeCounter++}"
    return (0 until count).map { prefix + ('a' + it) }
}

fun readEither(transaction: TransactionModel, keys: List<String>) {
    if (chance(0.5)) {
        scan(transaction, keys.first(), keys.last() + "0", if (chance(0.15)) " FOR SHARE" else "")
    } else {
        keys.shuffled(random).forEach { read(transaction, it, if (chance(0.15)) " FOR SHARE" else "") }
    }
}

fun writeSkew() {
    val keys = episodeKeys(2)
    if (chance(0.6)) {
        val loader = begin(isolation = "", track = false)
        keys.forEach { write(loader, it, "on") }
        finish(loader)
    }
    val first = begin(isolation = if (chance(0.85)) SERIALIZABLE else REPEATABLE_READ, track = false)
    val second = begin(isolation = if (chance(0.85)) SERIALIZABLE else randomIsolation(), track = false)
    readEither(first, keys)
    readEither(second, keys)
    write(first, keys[0], "off")
    write(second, keys[1], "off")
    val order = listOf(first, second).shuffled(random)
    finish(order[0], commit = !chance(0.15))
    finish(order[1])
    if (chance(0.5)) {
        val retry = begin(isolation = SERIALIZABLE, track = false)
        readEither(retry, keys)
        finish(retry)
    }
}

fun phantom() {
    val (low, high) = episodeKeys(2)
    val members = (0 until 2 + random.nextInt(2)).map { begin(isolation = if (chance(0.9)) SERIALIZABLE else REPEATABLE_READ, track = false) }
    members.forEach { scan(it, low, high) }
    members.forEachIndexed { index, member -> write(member, low + ('a' + index), pick(listOf("kim", "lee", "park"))) }
    members.shuffled(random).forEach { finish(it) }
}

fun chainOfThree() {
    val (first, second) = episodeKeys(2)
    val reader = begin(isolation = SERIALIZABLE, track = false)
    val pivot = begin(isolation = SERIALIZABLE, track = false)
    val writer = begin(isolation = if (chance(0.85)) SERIALIZABLE else REPEATABLE_READ, track = false)
    read(reader, first)
    if (chance(0.3)) write(reader, first + "z")
    write(pivot, first)
    read(pivot, second)
    write(writer, second)
    val order = listOf(reader, pivot, writer).shuffled(random)
    finish(order[0], commit = !chance(0.1))
    order.drop(1).forEach { finish(it) }
}

fun notConcurrent() {
    val (first, second) = episodeKeys(2)
    val early = begin(isolation = SERIALIZABLE, track = false)
    read(early, first)
    if (chance(0.5)) write(early, second)
    finish(early)
    val late = begin(isolation = SERIALIZABLE, track = false)
    val other = begin(isolation = SERIALIZABLE, track = false)
    write(late, first)
    read(late, second)
    write(other, second)
    finish(other)
    finish(late)
}

fun conflictBeforeStructure() {
    val keys = episodeKeys(3)
    val first = begin(isolation = SERIALIZABLE, track = false)
    val second = begin(isolation = SERIALIZABLE, track = false)
    read(first, keys[0])
    read(second, keys[1])
    write(first, keys[1])
    write(first, keys[2])
    write(second, keys[0])
    finish(first)
    write(second, keys[2])
    finish(second)
}

fun deadlockInStructure() {
    val (first, second) = episodeKeys(2)
    val older = begin(isolation = SERIALIZABLE, track = false)
    val younger = begin(isolation = SERIALIZABLE, track = false)
    read(older, second)
    read(younger, first)
    write(older, first)
    write(younger, second)
    write(older, second)
    write(younger, first)
    finish(younger)
    finish(older)
}

fun runEpisode() {
    when (weighted("skew" to 4, "phantom" to 2, "chain" to 3, "concurrent" to 1, "conflict" to 1, "deadlock" to 1)) {
        "skew" -> writeSkew()
        "phantom" -> phantom()
        "chain" -> chainOfThree()
        "concurrent" -> notConcurrent()
        "conflict" -> conflictBeforeStructure()
        else -> deadlockInStructure()
    }
}

fun normalOperation(transaction: TransactionModel) {
    when (weighted("read" to 6, "write" to 4, "scan" to 3)) {
        "read" -> read(transaction, pick(normalKeys), weighted("" to 6, " FOR SHARE" to 1, " FOR UPDATE" to 1))
        "write" -> write(transaction, pick(normalKeys))
        else -> {
            val first = pick(normalKeys)
            val second = if (chance(0.2)) first + "0" else pick(normalKeys)
            if (first != second) scan(transaction, minOf(first, second), maxOf(first, second), weighted("" to 6, " FOR SHARE" to 1, " FOR UPDATE" to 1))
        }
    }
}

fun runNormal() {
    if (chance(0.7)) {
        val loader = begin(isolation = "")
        normalKeys.filter { chance(0.6) }.forEach { write(loader, it) }
        finish(loader)
    }
    var begun = 0
    while (begun < options.transactions || active.isNotEmpty()) {
        val canBegin = begun < options.transactions && active.size < options.active
        val action = weighted(
            "begin" to if (canBegin) (if (active.isEmpty()) 100 else 6) else 0,
            "operation" to if (active.isEmpty()) 0 else 16,
            "commit" to if (active.isEmpty()) 0 else 4,
            "rollback" to if (active.isEmpty()) 0 else 1,
            "episode" to 2,
            "malformed" to 2,
            "stale" to 1,
            "blank" to 1,
        )
        when (action) {
            "begin" -> {
                begin()
                begun++
            }
            "operation" -> normalOperation(pick(active))
            "commit" -> finish(pick(active))
            "rollback" -> finish(pick(active), commit = false)
            "episode" -> runEpisode()
            "malformed" -> emit(malformedLine())
            "stale" -> staleLine(normalKeys)
            else -> emit(pick(listOf("", " ", "   ")))
        }
    }
}

val sortedScaleKeys: List<String> by lazy { (0 until options.keys).map { "k${it.toString(36)}" }.sorted() }
val hotScaleKeys: List<String> by lazy { (0 until 400).map { pick(sortedScaleKeys) }.distinct() }

fun scaleKey(): String = if (chance(0.15)) pick(hotScaleKeys) else pick(sortedScaleKeys)

fun scaleOperation(transaction: TransactionModel) {
    if (transaction.scans && transaction.plannedOperations == 1) {
        val start = random.nextInt(sortedScaleKeys.size - 8)
        scan(transaction, sortedScaleKeys[start], sortedScaleKeys[start + 2 + random.nextInt(5)], if (chance(0.1)) " FOR SHARE" else "")
    } else if (chance(0.6)) {
        read(transaction, scaleKey(), if (chance(0.05)) pick(listOf(" FOR SHARE", " FOR UPDATE")) else "")
    } else {
        write(transaction, scaleKey(), "${random.nextInt(100)}")
    }
    transaction.plannedOperations--
}

fun runScale() {
    val loader = begin(isolation = "")
    hotScaleKeys.forEach { write(loader, it, "0") }
    finish(loader)
    var begun = 0
    while (begun < options.transactions || active.isNotEmpty()) {
        if (begun < options.transactions && active.size < options.active) {
            val serializable = chance(0.85)
            val isolation = if (serializable) SERIALIZABLE else pick(listOf(REPEATABLE_READ, "", REPEATABLE_READ))
            begin(isolation, plannedOperations = 1 + random.nextInt(6), scans = serializable && chance(0.9))
            begun++
            continue
        }
        when {
            chance(0.002) -> emit(malformedLine())
            chance(0.001) -> staleLine(sortedScaleKeys)
        }
        val transaction = active[(active.size * random.nextDouble().pow(2)).toInt()]
        if (transaction.plannedOperations > 0) scaleOperation(transaction) else finish(transaction, commit = !chance(0.05))
    }
}

if (largeScale) runScale() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
