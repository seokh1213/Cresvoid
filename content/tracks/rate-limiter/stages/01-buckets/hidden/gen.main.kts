import kotlin.random.Random

class Options(val keys: Int, val requests: Int, val flood: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--keys" to "40", "--requests" to "400", "--flood" to "0", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        keys = values.getValue("--keys").toInt(),
        requests = values.getValue("--requests").toInt(),
        flood = values.getValue("--flood").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.requests >= 100_000
val output = StringBuilder()

val MAX_TIME = 1_000_000_000_000L
val MILLI_TOKENS_PER_TOKEN = 1000L

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

class Target(val limiter: LimiterModel, val key: String, val time: Long)

sealed class LimiterModel(val name: String) {
    abstract val definition: String

    abstract fun judge(key: String, time: Long): List<Long>
}

class TokenModel(name: String, val capacity: Long, val refillPerSecond: Long) : LimiterModel(name) {
    private class Bucket(val time: Long, val milliTokens: Long)

    private val buckets = HashMap<String, Bucket>()
    override val definition = "TOKEN $name $capacity $refillPerSecond"

    override fun judge(key: String, time: Long): List<Long> {
        val capacityMilliTokens = capacity * MILLI_TOKENS_PER_TOKEN
        val bucket = buckets[key]
        val milliTokens = if (bucket == null) {
            capacityMilliTokens
        } else {
            minOf(capacityMilliTokens, bucket.milliTokens + refillPerSecond * (time - bucket.time))
        }
        if (milliTokens >= MILLI_TOKENS_PER_TOKEN) {
            buckets[key] = Bucket(time, milliTokens - MILLI_TOKENS_PER_TOKEN)
            return emptyList()
        }
        buckets[key] = Bucket(time, milliTokens)
        val retryAt = time + Math.ceilDiv(MILLI_TOKENS_PER_TOKEN - milliTokens, refillPerSecond)
        return listOf(retryAt, retryAt - 1)
    }
}

class LeakyModel(name: String, val queueLength: Long, val leakInterval: Long) : LimiterModel(name) {
    private val lastReleases = HashMap<String, Long>()
    override val definition = "LEAKY $name $queueLength $leakInterval"

    override fun judge(key: String, time: Long): List<Long> {
        val lastRelease = lastReleases[key]
        val waiting = if (lastRelease == null || lastRelease <= time) 0 else Math.ceilDiv(lastRelease - time, leakInterval)
        if (lastRelease != null && waiting >= queueLength) {
            val slotOpensAt = lastRelease - (waiting - 1) * leakInterval
            return listOf(slotOpensAt, slotOpensAt - 1)
        }
        val release = if (lastRelease == null) time else maxOf(time, lastRelease + leakInterval)
        lastReleases[key] = release
        return if (release > time) listOf(release) else emptyList()
    }
}

val UNKNOWN_COMMANDS = listOf(
    "req", "Req", "REQS", "REQUEST", "token", "Token", "TOKENS", "TOKEN_BUCKET", "leaky", "Leaky", "LEAK",
    "LEAKY_BUCKET", "PING", "HELP", "QUIT", "LIMIT", "ALLOW", "ERR",
)
val BAD_NAMES = listOf("x".repeat(65), "ali/ce", "키", "a*b", "user@1", "a,b", "é", "a+b", "#", "a\\b")
val BAD_TIMES = listOf(
    "01", "-1", "+5", "1.0", "1e3", "0x1F", "00", "1000000000001", "99999999999999999999999", "١٢", "１２",
)
val SPECIAL_KEYS = listOf("a", "Z", "_", "-", ".", ":", "Alice", "alice", "ALICE", "k".repeat(64), "0", "...", "a:b.c-d_e")
val LIMITER_NAMES = listOf(
    "api", "login", "search:v2", "pay.charge", "upload-1", "q", "Q", "drip", "burst_7", "a", "10.0.0.1",
    "L".repeat(64), "slow", "gate",
)
val GHOST_LIMITERS = listOf("ghost", "API", "Api", "api2", "pay.Charge", "none", "x")

val limiters = ArrayList<LimiterModel>()
val defined = HashMap<String, LimiterModel>()
val targets = ArrayList<Target>()
var now = 0L
var nextFreshKey = 0
val usedKeys = ArrayList<String>()

fun keyName(index: Int): String = when (index % 7) {
    0 -> "user:$index"
    1 -> "10.${index / 65_536 % 256}.${index / 256 % 256}.${index % 256}"
    2 -> "k$index"
    3 -> "sess_${index.toString(36)}"
    4 -> "api-key.$index"
    5 -> "U${index}x"
    else -> "c:$index:m"
}

fun freshKeyProbability(remainingRequests: Int): Double =
    if (remainingRequests <= 0) 0.0 else ((options.keys - nextFreshKey).toDouble() / remainingRequests).coerceIn(0.0, 0.9)

fun nextKey(remainingRequests: Int): String {
    if (!largeScale && chance(0.04)) return pick(SPECIAL_KEYS)
    if (usedKeys.isEmpty() || (nextFreshKey < options.keys && chance(freshKeyProbability(remainingRequests)))) {
        val key = keyName(nextFreshKey++)
        usedKeys += key
        return key
    }
    val skewed = (usedKeys.size * Math.pow(random.nextDouble(), 3.0)).toInt().coerceIn(0, usedKeys.size - 1)
    return usedKeys[skewed]
}

fun define(limiter: LimiterModel) {
    emit(limiter.definition)
    if (limiter.name !in defined) {
        defined[limiter.name] = limiter
        limiters += limiter
    }
}

fun randomTokenModel(name: String): TokenModel = TokenModel(
    name,
    capacity = weighted(1L to 4, 2L to 4, 3L to 4, 5L to 3, 10L to 3, 50L to 1, 1_000L to 1, 1_000_000_000L to 1),
    refillPerSecond = weighted(
        1L to 4, 2L to 3, 3L to 4, 7L to 3, 10L to 2, 999L to 1, 1_000L to 1, 1_001L to 1, 333_333L to 1, 1_000_000L to 1,
    ),
)

fun randomLeakyModel(name: String): LeakyModel = LeakyModel(
    name,
    queueLength = weighted(1L to 4, 2L to 4, 3L to 3, 5L to 3, 10L to 2, 100L to 1),
    leakInterval = weighted(1L to 2, 7L to 2, 50L to 3, 100L to 4, 250L to 2, 1_000L to 2, 1_000_000L to 1),
)

fun pad(line: String): String = if (chance(0.03)) "  " + line.replace(" ", "   ") + " " else line

fun request(limiter: LimiterModel, key: String, timeArgument: Long) {
    emit(pad("REQ ${limiter.name} $key $timeArgument"))
    now = maxOf(now, timeArgument)
    val interesting = limiter.judge(key, now)
    if (!largeScale) {
        interesting.filter { it >= now }.forEach { targets += Target(limiter, key, it) }
        if (targets.size > 64) targets.subList(0, targets.size - 64).clear()
    }
}

fun backwardTime(): Long = now - 1 - random.nextLong(minOf(now, if (chance(0.1)) now else 5_000L))

fun stepFromNow(): Long = weighted(
    { 0L } to 30,
    { random.nextLong(1, 11) } to 24,
    { pick(listOf(50L, 100L, 250L, 333L, 334L, 500L, 1_000L)) } to 22,
    { random.nextLong(11, 1_000) } to 16,
    { random.nextLong(1_000, 100_000) } to 6,
    { random.nextLong(100_000, 1_000_000_000) } to 1,
)()

fun burstStep(): Long = weighted(0L to 5, 1L to 2, random.nextLong(2, 4) to 1, random.nextLong(4, 120) to 2)

fun normalTimeArgument(): Long = when {
    now > 0 && chance(0.03) -> backwardTime()
    else -> minOf(MAX_TIME, now + stepFromNow())
}

fun numberWord(range: LongRange): String = weighted(
    { range.first.toString() } to 2,
    { range.last.toString() } to 1,
    { random.nextLong(range.first, minOf(range.last, range.first + 20) + 1).toString() } to 4,
)()

fun malformedLine(): String {
    val limiter = if (limiters.isEmpty() || chance(0.2)) pick(GHOST_LIMITERS) else pick(limiters).name
    val key = if (usedKeys.isEmpty()) "k0" else pick(usedKeys)
    val name = pick(LIMITER_NAMES)
    return weighted(
        { "${pick(UNKNOWN_COMMANDS)} $limiter $key $now" } to 4,
        { pick(UNKNOWN_COMMANDS) } to 1,
        { "REQ $limiter $key" } to 2,
        { "REQ $limiter $key $now $now" } to 1,
        { "REQ" } to 1,
        { "REQ $limiter $key ${pick(BAD_TIMES)}" } to 4,
        { "REQ $limiter ${pick(BAD_NAMES)} $now" } to 2,
        { "REQ ${pick(BAD_NAMES)} $key $now" } to 1,
        { "TOKEN $name ${pick(listOf("0", "1000000001", "007", "-1"))} ${numberWord(1L..1_000_000L)}" } to 1,
        { "TOKEN $name ${numberWord(1L..1_000_000_000L)} ${pick(listOf("0", "1000001", "1.5", "+1"))}" } to 1,
        { "LEAKY $name ${pick(listOf("0", "1000001", "02"))} ${numberWord(1L..1_000_000L)}" } to 1,
        { "LEAKY $name ${numberWord(1L..1_000_000L)} ${pick(listOf("0", "1000001", "-100"))}" } to 1,
        { "TOKEN ${pick(BAD_NAMES)} 5 5" } to 1,
        { "TOKEN $name 5" } to 1,
        { "LEAKY $name 5 5 5" } to 1,
        { "REQ $limiter $key $now\r" } to 1,
        { "REQ\t$limiter $key $now" } to 1,
        { "\t" } to 1,
    )()
}

fun ghostRequest() {
    val ghost = GHOST_LIMITERS.filter { it !in defined }.ifEmpty { return }
    val timeArgument = if (chance(0.5)) now + random.nextLong(1, 1_000_000) else now
    emit("REQ ${pick(ghost)} ${pick(usedKeys.ifEmpty { listOf("k0") })} ${minOf(MAX_TIME, timeArgument)}")
}

fun redefine() {
    val existing = pick(limiters)
    val name = existing.name
    emit(if (chance(0.5)) randomTokenModel(name).definition else randomLeakyModel(name).definition)
}

fun burstSize(limiter: LimiterModel): Int =
    if (limiter is LeakyModel && limiter.queueLength <= 10 && chance(0.6)) {
        limiter.queueLength.toInt() + 2 + random.nextInt(3)
    } else {
        2 + random.nextInt(11)
    }

fun burst(remainingRequests: Int): Int {
    val leakyLimiters = limiters.filterIsInstance<LeakyModel>()
    val limiter = if (leakyLimiters.isNotEmpty() && chance(0.5)) pick(leakyLimiters) else pick(limiters)
    val key = nextKey(remainingRequests)
    val size = minOf(remainingRequests, burstSize(limiter))
    request(limiter, key, normalTimeArgument())
    repeat(size - 1) { request(limiter, key, minOf(MAX_TIME, now + burstStep())) }
    return size
}

fun targetedRequest(): Boolean {
    val reachable = targets.filter { it.time >= now }
    if (reachable.isEmpty()) return false
    val target = pick(reachable)
    request(target.limiter, target.key, target.time)
    return true
}

fun runNormal() {
    val names = LIMITER_NAMES.shuffled(random)
    val tokenCount = 1 + random.nextInt(3)
    val leakyCount = 1 + random.nextInt(3)
    names.take(tokenCount).forEach { define(randomTokenModel(it)) }
    names.drop(tokenCount).take(leakyCount).forEach { define(randomLeakyModel(it)) }
    val lateLimiter = randomTokenModel(GHOST_LIMITERS.last())
    val lateAt = options.requests / 2
    var sent = 0
    while (sent < options.requests) {
        if (sent == lateAt) define(lateLimiter)
        when (weighted("request" to 60, "burst" to 8, "targeted" to 14, "ghost" to 3, "redefine" to 1, "malformed" to 5, "blank" to 2)) {
            "request" -> {
                request(pick(limiters), nextKey(options.requests - sent), normalTimeArgument())
                sent++
            }
            "burst" -> sent += burst(options.requests - sent)
            "targeted" -> if (targetedRequest()) sent++
            "ghost" -> ghostRequest()
            "redefine" -> redefine()
            "malformed" -> emit(malformedLine())
            else -> emit(pick(listOf("", " ", "   ")))
        }
    }
    if (chance(0.5)) {
        repeat(3) { request(pick(limiters), nextKey(0), MAX_TIME) }
        emit("REQ ${pick(limiters).name} ${pick(usedKeys)} ${MAX_TIME + 1}")
        request(pick(limiters), nextKey(0), MAX_TIME - 1)
    }
}

val FLOOD_KEYS = listOf("flood:a" to 6, "flood:b" to 3, "flood:c" to 1)

fun scaleStep(progress: Double, remainingLines: Int): Long = when {
    progress < 0.1 -> weighted(0L to 3, random.nextLong(1, 30) to 5, random.nextLong(30, 400) to 2)
    progress < 0.85 -> weighted(0L to 6, 1L to 3, random.nextLong(2, 8) to 1)
    else -> {
        val average = (MAX_TIME - now) / maxOf(1, remainingLines)
        random.nextLong(0, 2 * average + 1)
    }
}

fun runScale() {
    listOf(
        TokenModel("api", 10, 1),
        TokenModel("login", 3, 1),
        TokenModel("search", 100, 50),
        TokenModel("bulk", 1_000_000_000, 1_000_000),
        TokenModel("slow", 2, 3),
        TokenModel("burst", 1_000, 7),
        LeakyModel("queue", 5, 100),
        LeakyModel("drip", 50, 1_000),
        LeakyModel("tiny", 1, 1),
    ).forEach(::define)
    val flood = LeakyModel("flood", 100_000, 1_000_000)
    define(flood)
    val ordinaryLimiters = limiters.filter { it !== flood }
    val ordinaryRequests = options.requests - options.flood
    var ordinarySent = 0
    var floodSent = 0
    val total = options.requests
    while (ordinarySent + floodSent < total) {
        val sent = ordinarySent + floodSent
        val progress = sent.toDouble() / total
        val remaining = total - sent
        when {
            chance(0.004) -> emit(malformedLine())
            chance(0.002) -> ghostRequest()
            chance(0.001) -> emit("")
        }
        val timeArgument = if (now > 0 && chance(0.01)) backwardTime() else minOf(MAX_TIME, now + scaleStep(progress, remaining))
        val floodPhase = progress >= 0.1 && progress < 0.85
        val floodWanted = options.flood - floodSent
        val floodShare = if (floodPhase) floodWanted.toDouble() / maxOf(1, (0.85 * total).toInt() - sent) else 0.0
        if (floodWanted > 0 && (chance(floodShare) || ordinarySent >= ordinaryRequests)) {
            request(flood, weighted(*FLOOD_KEYS.toTypedArray()), timeArgument)
            floodSent++
        } else {
            request(pick(ordinaryLimiters), nextKey(ordinaryRequests - ordinarySent), timeArgument)
            ordinarySent++
        }
    }
    repeat(5) { request(pick(ordinaryLimiters), nextKey(0), MAX_TIME) }
}

if (largeScale) runScale() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
