import java.util.PriorityQueue
import kotlin.random.Random

class Options(val batches: Int, val items: Int, val heavyItems: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--batches" to "12", "--items" to "8", "--heavy" to "0", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        batches = values.getValue("--batches").toInt(),
        items = values.getValue("--items").toInt(),
        heavyItems = values.getValue("--heavy").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.items >= 1_000
val output = StringBuilder()

val MAX_NUMBER = 1_000_000_000L
val SERVER_ERRORS = listOf(500, 502, 503, 504, 599)
val CLIENT_ERRORS = listOf(400, 404, 409, 429, 499)

fun emit(line: String) {
    output.append(line).append('\n')
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun between(low: Long, high: Long): Long = random.nextLong(low, high + 1)

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

class LockTimes(val lock: Long, val unlock: Long)

class Retry(val maxAttempts: Int, val timeout: Long, val base: Long, val cap: Long) {
    fun waitAfter(attempt: Int): Long = minOf(base * (1L shl (attempt - 1)), cap)
}

class Attempt(val latency: Long, val code: Int?) {
    val token: String get() = "$latency:${code ?: "OK"}"
}

class Product(val name: String, val attempts: List<Attempt>) {
    fun attempt(number: Int): Attempt = attempts[minOf(number, attempts.size) - 1]
}

enum class Phase { LOCK, FETCH, BACKOFF, UNLOCK }

class Segment(val phase: Phase, val start: Long, val end: Long)

// 취소 시각을 고르려고 취소가 없을 때 상품 하나가 지나는 구간을 계산한다.
fun timeline(product: Product, start: Long, lockTimes: LockTimes, retry: Retry): List<Segment> {
    val segments = mutableListOf(Segment(Phase.LOCK, start, start + lockTimes.lock))
    var attemptStart = start + lockTimes.lock
    var number = 1
    while (true) {
        val attempt = product.attempt(number)
        val response = attemptStart + attempt.latency
        val timeoutEnd = attemptStart + retry.timeout
        val answered = response < timeoutEnd
        val end = if (answered) response else timeoutEnd
        segments += Segment(Phase.FETCH, attemptStart, end)
        val retryable = !answered || (attempt.code != null && attempt.code >= 500)
        if (!retryable || number == retry.maxAttempts) break
        val resume = end + retry.waitAfter(number)
        segments += Segment(Phase.BACKOFF, end, resume)
        attemptStart = resume
        number++
    }
    val decided = segments.last().end
    segments += Segment(Phase.UNLOCK, decided, decided + lockTimes.unlock)
    return segments
}

fun schedule(products: List<Product>, limit: Int, lockTimes: LockTimes, retry: Retry): List<List<Segment>> {
    val freeSlots = PriorityQueue<Long>()
    repeat(minOf(limit, products.size)) { freeSlots += 0L }
    return products.map { product ->
        val segments = timeline(product, freeSlots.poll(), lockTimes, retry)
        freeSlots += segments.last().end
        segments
    }
}

val NAME_WORDS = listOf("sku", "item", "p", "tv", "shoe", "bag", "x", "mug", "lamp", "desk")

fun nameCandidate(): String = weighted(
    { "${pick(NAME_WORDS)}${random.nextInt(1, 40)}" } to 6,
    { "${pick(NAME_WORDS)}-${random.nextInt(0, 1000)}" } to 3,
    { ('a' + random.nextInt(26)).toString() } to 1,
    { random.nextInt(0, 100).toString() } to 1,
    { "p1" + "0".repeat(random.nextInt(0, 3)) } to 1,
    { "-" + ('a' + random.nextInt(26)) } to 1,
    { "z".repeat(random.nextInt(15, 21)) } to 1,
)()

fun distinctNames(count: Int): List<String> {
    val names = LinkedHashSet<String>()
    while (names.size < count) {
        names += if (largeScale || chance(0.2)) "sku-${random.nextInt(0, 1_000_000)}" else nameCandidate()
    }
    return names.toList()
}

fun randomLockTimes(): LockTimes = LockTimes(
    lock = weighted({ between(1, 30) } to 8, { 1L } to 1, { between(31, 500) } to 1)(),
    unlock = weighted({ between(2, 30) } to 8, { 1L } to 1, { between(31, 500) } to 1)(),
)

fun randomRetry(): Retry {
    if (!largeScale && chance(0.08)) {
        val base = between(600_000_000, MAX_NUMBER)
        return Retry(maxAttempts = random.nextInt(3, 11), timeout = between(5, 100), base = base, cap = MAX_NUMBER)
    }
    val base = weighted({ between(1, 60) } to 6, { 1L } to 1, { between(61, 1_000) } to 1)()
    val cap = weighted(
        { base * (1L shl random.nextInt(0, 5)) } to 4,
        { base } to 1,
        { between(base, base * 40) } to 3,
        { MAX_NUMBER } to 1,
        { between(1, base) } to 1,
    )()
    val maxAttempts = if (largeScale) random.nextInt(2, 6) else weighted(1 to 1, 2 to 2, 3 to 4, 4 to 2, 5 to 1, 10 to 1)
    return Retry(maxAttempts = maxAttempts, timeout = between(5, 200), base = base, cap = minOf(cap, MAX_NUMBER))
}

fun randomAttempt(retry: Retry): Attempt {
    val latency = weighted(
        { between(1, retry.timeout - 1) } to 6,
        { retry.timeout } to 1,
        { retry.timeout + between(1, 60) } to 1,
    )()
    val code = weighted({ null } to 5, { pick(SERVER_ERRORS) } to 3, { pick(CLIENT_ERRORS) } to 2)()
    return Attempt(latency, code)
}

fun randomProducts(count: Int, retry: Retry): List<Product> =
    distinctNames(count).map { name ->
        val attemptCount = weighted(1 to 5, 2 to 4, 3 to 2, 4 to 1)
        Product(name, List(attemptCount) { randomAttempt(retry) })
    }

fun batchSize(): Int =
    if (largeScale) {
        options.items + random.nextInt(-options.items / 10, options.items / 10 + 1)
    } else {
        weighted({ options.items } to 3, { random.nextInt(1, 2 * options.items + 1) } to 6, { 1 } to 1)()
    }

fun randomLimit(size: Int): Int =
    if (largeScale) {
        random.nextInt(20, 61)
    } else {
        weighted(1 to 2, 2 to 3, 3 to 3, size to 1, size + 3 to 1, random.nextInt(1, size + 1) to 2, 100_000 to 1)
    }

fun interestingCancelTime(timelines: List<List<Segment>>): Long? {
    val segments = timelines.flatten()
    val finish = timelines.maxOf { it.last().end }
    val ends = { phase: Phase -> segments.filter { it.phase == phase }.map { it.end } }
    val candidates: List<Long> = weighted(
        { emptyList<Long>() } to 7,
        { ends(Phase.LOCK) } to 1,
        { ends(Phase.FETCH) } to 2,
        { ends(Phase.BACKOFF) } to 1,
        { ends(Phase.UNLOCK) } to 2,
        { timelines.map { it.first().start }.filter { it > 0 } } to 1,
        { segments.filter { it.end - it.start >= 2 }.map { between(it.start + 1, it.end - 1) } } to 2,
        { listOf(finish) } to 1,
        { listOf(finish + between(1, 50)) } to 1,
        { listOf(between(1, finish)) } to 1,
    )()
    return candidates.filter { it in 1..MAX_NUMBER }.takeIf { it.isNotEmpty() }?.let(::pick)
}

fun unlockInProgressTime(timelines: List<List<Segment>>): Long {
    val finish = timelines.maxOf { it.last().end }
    val unlocking = timelines.map { it.last() }.filter { it.end - it.start >= 2 }
    val midway = unlocking.filter { it.start >= finish * 3 / 10 && it.end <= finish * 7 / 10 }
    val chosen = pick(midway.ifEmpty { unlocking })
    return between(chosen.start + 1, chosen.end - 1)
}

fun spaced(line: String): String =
    if (chance(0.03)) "  " + line.split(' ').joinToString("   ") + " " else line

var lockTimes = randomLockTimes()
var retry = randomRetry()
emit("LOCK ${lockTimes.lock} ${lockTimes.unlock}")
emit("RETRY ${retry.maxAttempts} ${retry.timeout} ${retry.base} ${retry.cap}")
var traceOn = true
repeat(options.batches) { batchIndex ->
    if (batchIndex > 0 && chance(if (largeScale) 1.0 else 0.4)) {
        lockTimes = if (largeScale) LockTimes(between(1, 20), between(5, 30)) else randomLockTimes()
        emit(spaced("LOCK ${lockTimes.lock} ${lockTimes.unlock}"))
    }
    if (batchIndex > 0 && chance(if (largeScale) 1.0 else 0.5)) {
        retry = randomRetry()
        emit(spaced("RETRY ${retry.maxAttempts} ${retry.timeout} ${retry.base} ${retry.cap}"))
    }
    val products = randomProducts(batchSize(), retry)
    val limit = randomLimit(products.size)
    val timelines = schedule(products, limit, lockTimes, retry)
    val cancelAt = when {
        largeScale -> if (batchIndex % 2 == 1) unlockInProgressTime(timelines) else null
        else -> interestingCancelTime(timelines)
    }
    if (!largeScale) {
        val switchTrace = if (traceOn) chance(0.1) else chance(0.4)
        if (switchTrace) traceOn = !traceOn
        if (switchTrace) emit(if (traceOn) "TRACE ON" else "TRACE OFF")
    }
    for (product in products) {
        emit(spaced("ITEM ${product.name} ${product.attempts.joinToString(" ") { it.token }}"))
    }
    if (!largeScale && chance(0.03)) emit("")
    emit(spaced(if (cancelAt == null) "BATCH $limit" else "BATCH $limit CANCEL $cancelAt"))
}

// 스펙 7.2: 시작할 상품을 고를 때마다 상품 목록을 훑는 풀이가 제한 시간을 넘도록 사건 줄 없이 큰 배치를 하나 더 실행한다.
if (options.heavyItems > 0) {
    lockTimes = LockTimes(between(1, 20), between(5, 30))
    retry = randomRetry()
    emit("TRACE OFF")
    emit("LOCK ${lockTimes.lock} ${lockTimes.unlock}")
    emit("RETRY ${retry.maxAttempts} ${retry.timeout} ${retry.base} ${retry.cap}")
    val heavyCount = options.heavyItems + random.nextInt(-options.heavyItems / 10, options.heavyItems / 10 + 1)
    for (product in randomProducts(heavyCount, retry)) {
        emit("ITEM ${product.name} ${product.attempts.joinToString(" ") { it.token }}")
    }
    emit("BATCH ${random.nextInt(20, 61)}")
}

System.out.write(output.toString().encodeToByteArray())
System.out.flush()
