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

val CURATED_KEYS = listOf("a", "b", "c", "d", "k", "k1", "k10", "k2", "m", "x", "07", "7", "doc", "doc0", "mid", "w")
val VALUES = listOf("0", "1", "2", "7", "07", "10", "11", "12", "on", "off", "abcdefghijklmnop")
val SPECIAL_NAMES = listOf("a", "k", "doc", "z9", "abcdefghijklmnop", "m0", "init", "q", "t")
val NEVER_BEGUN_NAMES = listOf("ghost", "nobody", "u1", "zz")
val UNKNOWN_COMMANDS = listOf("begin", "Begin", "SELECT", "READS", "LOCK", "UNLOCK", "WAIT", "ABORT", "read", "Scan", "END")
val LOCK_CLAUSES = listOf("", " FOR SHARE", " FOR UPDATE")

class TransactionModel(val name: String, var plannedOperations: Int = 0)

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

fun begin(isolation: String? = null, plannedOperations: Int = 0, track: Boolean = true): TransactionModel {
    val name = freshName()
    usedNames += name
    val isolationText = isolation ?: weighted("" to 4, " READ COMMITTED" to 1, " REPEATABLE READ" to 4)
    emit(pad("BEGIN $name$isolationText"))
    return TransactionModel(name, plannedOperations).also { if (track) active += it }
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
        { "${pick(UNKNOWN_COMMANDS)} $name $key" } to 3,
        { pick(UNKNOWN_COMMANDS) } to 1,
        { "READ\t$name $key" } to 1,
        { "BEGIN ${pick(listOf("1t", "T1", "abcdefghijklmnopq"))}" } to 1,
        { "BEGIN $name ${pick(listOf("READ UNCOMMITTED", "REPEATABLE", "SERIALIZABLE READ", "serializable", "Serializable"))}" } to 2,
        { "READ $name $key ${pick(listOf("FOR", "FOR DELETE", "for update", "FOR share", "FOR UPDATE NOW", "SHARE", "FOR SHARE UPDATE"))}" } to 4,
        { "READ $name ${pick(listOf("K", "k-1", "k\r"))} FOR SHARE" } to 1,
        { "READ T1 $key FOR UPDATE" } to 1,
        { "WRITE $name $key ${pick(listOf("Big", "12345678901234567"))}" } to 1,
        { "WRITE $name $key 1 FOR UPDATE" } to 1,
        { "WRITE $name $key" } to 1,
        { "SCAN $name $key $key FOR SHARE" } to 1,
        { "SCAN $name m a FOR UPDATE" } to 1,
        { "SCAN $name a m FOR UPDATE now" } to 1,
        { "SCAN $name a m FOR" } to 1,
        { "SCAN $name a FOR SHARE" } to 1,
        { "COMMIT $name FOR UPDATE" } to 1,
        { "ROLLBACK" } to 1,
    )()
}

fun staleLine(keys: List<String>) {
    val ended = endedNames.isNotEmpty() && chance(0.75)
    val name = if (ended) pick(endedNames) else pick(NEVER_BEGUN_NAMES)
    val key = pick(keys)
    emit(
        weighted(
            "READ $name $key" to 2,
            "READ $name $key FOR UPDATE" to 2,
            "WRITE $name $key 1" to 2,
            "SCAN $name a m FOR SHARE" to 1,
            "COMMIT $name" to 2,
            "ROLLBACK $name" to 2,
            "BEGIN $name" to if (ended) 3 else 0,
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

fun upgradeDeadlock() {
    val (key) = episodeKeys(1)
    val first = begin(track = false)
    val second = begin(track = false)
    read(first, key, " FOR SHARE")
    read(second, key, " FOR SHARE")
    write(first, key)
    if (chance(0.5)) read(first, key)
    write(second, key)
    if (chance(0.5)) finish(second)
    finish(first)
}

fun cycleDeadlock() {
    val size = 2 + random.nextInt(2)
    val keys = episodeKeys(size)
    val members = (0 until size).map { begin(track = false) }
    members.forEachIndexed { index, member -> write(member, keys[index]) }
    val closingOrder = (0 until size).shuffled(random)
    for (index in closingOrder) {
        val member = members[index]
        write(member, keys[(index + 1) % size])
        if (chance(0.4)) read(member, keys[index], pick(LOCK_CLAUSES))
        if (chance(0.3)) emit("COMMIT ${member.name}")
    }
    members.forEach { emit("COMMIT ${it.name}") }
}

fun arrowWhileWaiting() {
    val (shared, held) = episodeKeys(2)
    val reader = begin(track = false)
    val writer = begin(track = false)
    val joiner = begin(track = false)
    write(writer, held)
    read(reader, shared, " FOR SHARE")
    write(writer, shared)
    read(joiner, shared, " FOR SHARE")
    read(joiner, held, pick(listOf(" FOR SHARE", " FOR UPDATE")))
    finish(reader)
    finish(writer)
    finish(joiner)
}

fun rangeBoundary() {
    val keys = episodeKeys(4)
    val scanner = begin(isolation = pick(listOf(" REPEATABLE READ", "")), track = false)
    val inserter = begin(track = false)
    val boundary = begin(track = false)
    write(scanner, keys[1])
    scan(scanner, keys[0], keys[2], pick(listOf(" FOR SHARE", " FOR UPDATE")))
    write(inserter, keys[0] + "0")
    write(boundary, keys[2])
    read(boundary, keys[1], " FOR SHARE")
    scan(scanner, keys[0], keys[3])
    if (chance(0.5)) finish(boundary) else finish(boundary, commit = false)
    finish(scanner, commit = chance(0.7))
    finish(inserter)
}

fun resumeOrder() {
    val (first, second) = episodeKeys(2)
    val holder = begin(track = false)
    write(holder, first)
    write(holder, second)
    val early = begin(track = false)
    val late = begin(track = false)
    read(late, second, pick(listOf(" FOR SHARE", " FOR UPDATE")))
    read(early, first, " FOR UPDATE")
    write(early, second)
    emit("BEGIN ${early.name}")
    emit("READ ${early.name} K")
    finish(early)
    finish(holder, commit = chance(0.8))
    finish(late)
}

fun repeatableWaitConflict() {
    val (key) = episodeKeys(1)
    val first = begin(isolation = " REPEATABLE READ", track = false)
    val second = begin(isolation = " REPEATABLE READ", track = false)
    write(first, key)
    write(second, key)
    read(second, key)
    finish(first, commit = chance(0.6))
    finish(second)
}

fun runEpisode() {
    when (weighted("upgrade" to 3, "cycle" to 3, "arrow" to 2, "range" to 2, "resume" to 2, "repeatable" to 2)) {
        "upgrade" -> upgradeDeadlock()
        "cycle" -> cycleDeadlock()
        "arrow" -> arrowWhileWaiting()
        "range" -> rangeBoundary()
        "resume" -> resumeOrder()
        else -> repeatableWaitConflict()
    }
}

fun normalRange(): Pair<String, String>? {
    val first = pick(normalKeys)
    val second = if (chance(0.2)) pick(normalKeys) + "0" else pick(normalKeys)
    if (first == second) return null
    return minOf(first, second) to maxOf(first, second)
}

fun normalOperation(transaction: TransactionModel) {
    when (weighted("read" to 5, "write" to 5, "scan" to 2)) {
        "read" -> read(transaction, pick(normalKeys), weighted("" to 3, " FOR SHARE" to 2, " FOR UPDATE" to 2))
        "write" -> write(transaction, pick(normalKeys))
        else -> normalRange()?.let { (start, end) -> scan(transaction, start, end, weighted("" to 2, " FOR SHARE" to 1, " FOR UPDATE" to 1)) }
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
            "episode" to 1,
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

val LONG_LIVED = 40
val PILES = 3

val lockedKeyCount: Int get() = options.keys
val ordinaryKeys: List<String> by lazy { (0 until 20_000).map { "o$it" }.sorted() }

class LongLived(val transaction: TransactionModel, val exclusiveKeys: List<String>, val lockedKeys: List<String>)

fun lockLongLived(): List<LongLived> {
    val keys = (0 until lockedKeyCount).map { "l$it" }.sorted()
    val blockSize = keys.size / LONG_LIVED
    return (0 until LONG_LIVED).map { index ->
        val transaction = begin(isolation = pick(listOf("", " REPEATABLE READ")), track = false)
        val sortedBlock = keys.subList(index * blockSize, (index + 1) * blockSize)
        val exclusive = ArrayList<String>()
        for (key in sortedBlock.shuffled(random)) {
            when (weighted("write" to 5, "update" to 2, "share" to 3)) {
                "write" -> write(transaction, key, "${random.nextInt(10)}").also { exclusive += key }
                "update" -> read(transaction, key, " FOR UPDATE").also { exclusive += key }
                else -> read(transaction, key, " FOR SHARE")
            }
        }
        repeat(2) {
            val start = random.nextInt(sortedBlock.size - 40)
            scan(transaction, sortedBlock[start], sortedBlock[start + 1 + random.nextInt(30)], " FOR SHARE")
        }
        LongLived(transaction, exclusive, sortedBlock)
    }
}

fun longTermWaiter(longLived: List<LongLived>) {
    val holder = pick(longLived)
    val waiter = begin(isolation = pick(listOf("", " REPEATABLE READ")), track = false)
    if (chance(0.7) || holder.exclusiveKeys.isEmpty()) {
        val key = pick(holder.lockedKeys)
        if (chance(0.5)) write(waiter, key, "${random.nextInt(10)}") else read(waiter, key, " FOR UPDATE")
    } else {
        read(waiter, pick(holder.exclusiveKeys), " FOR SHARE")
    }
    if (chance(0.3)) read(waiter, pick(ordinaryKeys))
    finish(waiter, commit = !chance(0.1))
}

class Pile(val key: String, val holder: TransactionModel, var remaining: Int)

fun startPile(index: Int, size: Int): Pile {
    val key = "hot$index"
    val holder = begin(isolation = "", track = false)
    write(holder, key, "0")
    return Pile(key, holder, size)
}

fun joinPile(pile: Pile) {
    val joiner = begin(isolation = "", track = false)
    when (weighted("write" to 7, "update" to 2, "share" to 1)) {
        "write" -> write(joiner, pile.key, "${random.nextInt(100)}")
        "update" -> read(joiner, pile.key, " FOR UPDATE")
        else -> read(joiner, pile.key, " FOR SHARE")
    }
    finish(joiner)
    pile.remaining--
}

fun scaleDeadlock() {
    val keys = (0 until 3).map { "d${episodeCounter}x$it" }
    episodeCounter++
    if (chance(0.5)) {
        val first = begin(isolation = "", track = false)
        val second = begin(isolation = "", track = false)
        read(first, keys[0], " FOR SHARE")
        read(second, keys[0], " FOR SHARE")
        write(first, keys[0])
        write(second, keys[0])
        finish(second)
        finish(first)
    } else {
        val members = (0 until 2 + random.nextInt(2)).map { begin(isolation = "", track = false) }
        members.forEachIndexed { index, member -> write(member, keys[index]) }
        members.forEachIndexed { index, member -> write(member, keys[(index + 1) % members.size]) }
        members.forEach { finish(it) }
    }
}

fun scaleOrdinaryOperation(transaction: TransactionModel) {
    when (weighted("read" to 40, "write" to 35, "scan" to 12)) {
        "read" -> read(transaction, pick(ordinaryKeys), weighted("" to 5, " FOR SHARE" to 2, " FOR UPDATE" to 2))
        "write" -> write(transaction, pick(ordinaryKeys), "${random.nextInt(100)}")
        else -> {
            val start = random.nextInt(ordinaryKeys.size - 8)
            val lockClause = weighted("" to 1, " FOR SHARE" to 1, " FOR UPDATE" to 1)
            scan(transaction, ordinaryKeys[start], ordinaryKeys[start + 2 + random.nextInt(5)], lockClause)
        }
    }
    transaction.plannedOperations--
}

fun runScale() {
    val longLived = lockLongLived()
    val pileSize = options.transactions * 3 / 20
    val longTermWaiters = options.transactions / 12
    val deadlockEpisodes = options.transactions / 60
    val ordinaryTarget = options.transactions - LONG_LIVED - PILES * (pileSize + 1) - longTermWaiters - deadlockEpisodes * 5 / 2
    require(ordinaryTarget > 0 && lockedKeyCount >= LONG_LIVED * 50) { "대규모 입력에는 --transactions 와 --keys 가 더 커야 한다" }
    val steps = ordinaryTarget * 7
    var ordinaryBegun = 0
    var waitersLeft = longTermWaiters
    var deadlocksLeft = deadlockEpisodes
    val pileStarts = (0 until PILES).map { steps * (1 + 3 * it) / (3 * PILES + 1) }
    val pileLength = steps / (3 * PILES + 1) * 2
    var pile: Pile? = null
    var pileIndex = 0
    var step = 0
    while (step < steps || active.isNotEmpty()) {
        step++
        if (pileIndex < PILES && step == pileStarts[pileIndex]) pile = startPile(pileIndex++, pileSize)
        pile?.let { current ->
            val stepsLeft = maxOf(1, pileStarts[pileIndex - 1] + pileLength - step)
            if (current.remaining > 0 && chance(current.remaining.toDouble() / stepsLeft)) joinPile(current)
            if (current.remaining == 0) {
                finish(current.holder)
                pile = null
            }
        }
        val waiterWindowEnd = if (waitersLeft > longTermWaiters / 5) steps / 8 else steps
        val waitersInWindow = if (waitersLeft > longTermWaiters / 5) waitersLeft - longTermWaiters / 5 else waitersLeft
        if (waitersLeft > 0 && chance(waitersInWindow.toDouble() / maxOf(1, waiterWindowEnd - step))) {
            longTermWaiter(longLived)
            waitersLeft--
        }
        if (deadlocksLeft > 0 && chance(deadlocksLeft.toDouble() / maxOf(1, steps - step))) {
            scaleDeadlock()
            deadlocksLeft--
        }
        when {
            chance(0.002) -> emit(malformedLine())
            chance(0.001) -> staleLine(ordinaryKeys)
        }
        if (ordinaryBegun < ordinaryTarget && active.size < options.active) {
            begin(isolation = pick(listOf("", " REPEATABLE READ")), plannedOperations = 3 + random.nextInt(5))
            ordinaryBegun++
            continue
        }
        if (active.isEmpty()) continue
        val transaction = pick(active)
        if (transaction.plannedOperations > 0) scaleOrdinaryOperation(transaction) else finish(transaction, commit = !chance(0.1))
    }
    longLived.forEach { finish(it.transaction, commit = !chance(0.2)) }
}

if (largeScale) runScale() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
