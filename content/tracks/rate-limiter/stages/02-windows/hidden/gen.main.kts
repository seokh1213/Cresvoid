import kotlin.random.Random

class Options(val keys: Int, val requests: Int, val reports: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--keys" to "30", "--requests" to "400", "--reports" to "12", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        keys = values.getValue("--keys").toInt(),
        requests = values.getValue("--requests").toInt(),
        reports = values.getValue("--reports").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.requests >= 100_000
val output = StringBuilder()

val MAX_TIME = 1_000_000_000_000L

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

sealed class LimiterModel(val name: String) {
    abstract val definition: String
}

class WindowModel(name: String, val limit: Long, val windowLength: Long) : LimiterModel(name) {
    override val definition = "WINDOW $name $limit $windowLength"
    val recentTimes = HashMap<String, Long>()
}

class TokenModel(name: String, capacity: Long, refillPerSecond: Long) : LimiterModel(name) {
    override val definition = "TOKEN $name $capacity $refillPerSecond"
}

class LeakyModel(name: String, queueLength: Long, leakInterval: Long) : LimiterModel(name) {
    override val definition = "LEAKY $name $queueLength $leakInterval"
}

val UNKNOWN_COMMANDS = listOf(
    "req", "Req", "REQS", "window", "Window", "WINDOWS", "WIN", "report", "Report", "REPORTS", "STATS", "token",
    "LEAK", "PING", "RESET",
)
val BAD_NAMES = listOf("x".repeat(65), "ali/ce", "키", "a*b", "user@1", "a,b", "é", "#")
val BAD_NUMBERS = listOf("01", "-1", "+5", "1.0", "1e3", "00", "99999999999999999999999", "١٢")
val SPECIAL_KEYS = listOf("a", "Z", "_", "-", ".", ":", "Alice", "alice", "k".repeat(64), "0", "a:b.c-d_e")
val WINDOW_NAMES = listOf("w", "minute", "sec:1", "odd.777", "hour", "edge", "W", "g-2", "n".repeat(64), "10.1.1.1")
val OTHER_NAMES = listOf("tb", "lb", "api", "queue")
val GHOST_LIMITERS = listOf("ghost", "WINDOW", "Minute", "none", "w2")

val limiters = ArrayList<LimiterModel>()
val defined = HashMap<String, LimiterModel>()
val boundaryTargets = ArrayList<Long>()
var now = 0L
var nextFreshKey = 0
val usedKeys = ArrayList<String>()

fun keyName(index: Int): String = when (index % 6) {
    0 -> "user:$index"
    1 -> "10.${index / 65_536 % 256}.${index / 256 % 256}.${index % 256}"
    2 -> "k$index"
    3 -> "sess_${index.toString(36)}"
    4 -> "api-key.$index"
    else -> "c:$index:m"
}

fun nextKey(remainingRequests: Int): String {
    if (!largeScale && chance(0.04)) return pick(SPECIAL_KEYS)
    val freshProbability = if (remainingRequests <= 0) 0.0 else ((options.keys - nextFreshKey).toDouble() / remainingRequests).coerceIn(0.0, 0.9)
    if (usedKeys.isEmpty() || (nextFreshKey < options.keys && chance(freshProbability))) {
        val key = keyName(nextFreshKey++)
        usedKeys += key
        return key
    }
    val skewed = (usedKeys.size * Math.pow(random.nextDouble(), 2.0)).toInt().coerceIn(0, usedKeys.size - 1)
    return usedKeys[skewed]
}

fun define(limiter: LimiterModel) {
    emit(limiter.definition)
    if (limiter.name !in defined) {
        defined[limiter.name] = limiter
        limiters += limiter
    }
}

fun randomWindowModel(name: String): WindowModel = WindowModel(
    name,
    limit = weighted(1L to 3, 2L to 4, 3L to 4, 5L to 3, 10L to 2, 50L to 1, 1_000_000_000L to 1),
    windowLength = weighted(
        1L to 1, 7L to 2, 100L to 3, 777L to 3, 1_000L to 4, 5_000L to 2, 60_000L to 1, 3_600_000L to 1, 1_000_000_000L to 1,
    ),
)

fun pad(line: String): String = if (chance(0.03)) "  " + line.replace(" ", "   ") + " " else line

val windowLimiters: List<WindowModel> get() = limiters.filterIsInstance<WindowModel>()

fun request(limiter: LimiterModel, key: String, timeArgument: Long) {
    emit(pad("REQ ${limiter.name} $key $timeArgument"))
    now = maxOf(now, timeArgument)
    if (!largeScale && limiter is WindowModel) {
        limiter.recentTimes[key] = now
        boundaryTargets += now + limiter.windowLength
        boundaryTargets += (now / limiter.windowLength + 1) * limiter.windowLength
        if (boundaryTargets.size > 48) boundaryTargets.subList(0, boundaryTargets.size - 48).clear()
    }
}

fun backwardTime(): Long = now - 1 - random.nextLong(minOf(now, if (chance(0.1)) now else 3_000L))

fun stepFromNow(): Long = weighted(
    { 0L } to 28,
    { random.nextLong(1, 11) } to 24,
    { pick(listOf(100L, 223L, 250L, 500L, 554L, 777L, 1_000L)) } to 20,
    { random.nextLong(11, 1_000) } to 18,
    { random.nextLong(1_000, 10_000) } to 7,
    { random.nextLong(10_000, 5_000_000) } to 3,
)()

fun alignedTime(): Long {
    val window = pick(windowLimiters).windowLength
    val boundary = (now / window + 1) * window
    return boundary + weighted(0L to 4, -1L to 2, 1L to 2)
}

fun nextTimeArgument(): Long = minOf(
    MAX_TIME,
    when {
        now > 0 && chance(0.03) -> backwardTime()
        chance(0.08) -> alignedTime()
        else -> now + stepFromNow()
    },
)

fun numberWord(range: LongRange): String = weighted(
    { range.first.toString() } to 2,
    { range.last.toString() } to 1,
    { random.nextLong(range.first, minOf(range.last, range.first + 20) + 1).toString() } to 4,
)()

fun malformedLine(): String {
    val limiter = if (limiters.isEmpty() || chance(0.2)) pick(GHOST_LIMITERS) else pick(limiters).name
    val key = if (usedKeys.isEmpty()) "k0" else pick(usedKeys)
    val name = pick(WINDOW_NAMES)
    return weighted(
        { "${pick(UNKNOWN_COMMANDS)} $limiter $key $now" } to 3,
        { pick(UNKNOWN_COMMANDS) } to 1,
        { "REQ $limiter $key" } to 1,
        { "REQ $limiter $key ${pick(BAD_NUMBERS)}" } to 2,
        { "REQ $limiter ${pick(BAD_NAMES)} $now" } to 1,
        { "WINDOW $name ${pick(listOf("0", "1000000001", "007", "-1"))} ${numberWord(1L..1_000_000_000L)}" } to 2,
        { "WINDOW $name ${numberWord(1L..1_000_000_000L)} ${pick(listOf("0", "1000000001", "1.5", "+1"))}" } to 2,
        { "WINDOW ${pick(BAD_NAMES)} 5 1000" } to 1,
        { "WINDOW $name 5" } to 1,
        { "WINDOW $name 5 1000 7" } to 1,
        { "REPORT" } to 2,
        { "REPORT $limiter $limiter" } to 1,
        { "REPORT ${pick(BAD_NAMES)}" } to 1,
        { "REPORT $limiter\r" } to 1,
        { "REPORT\t$limiter" } to 1,
    )()
}

fun reportLine() {
    val target = weighted(
        { pick(windowLimiters).name } to 8,
        { limiters.filter { it !is WindowModel }.ifEmpty { windowLimiters }.let(::pick).name } to 1,
        { GHOST_LIMITERS.filter { it !in defined }.ifEmpty { listOf("ghost") }.let(::pick) } to 1,
    )()
    emit(pad("REPORT $target"))
}

fun ghostRequest() {
    val ghost = GHOST_LIMITERS.filter { it !in defined }.ifEmpty { return }
    val timeArgument = if (chance(0.5)) now + random.nextLong(1, 100_000) else now
    emit("REQ ${pick(ghost)} ${pick(usedKeys.ifEmpty { listOf("k0") })} ${minOf(MAX_TIME, timeArgument)}")
}

fun redefine() {
    val name = pick(limiters).name
    emit(if (chance(0.6)) randomWindowModel(name).definition else "TOKEN $name 3 1")
}

fun windowEchoRequest(): Boolean {
    val limiter = pick(windowLimiters)
    val (key, time) = limiter.recentTimes.entries.filter { it.value + limiter.windowLength >= now }
        .ifEmpty { return false }
        .let(::pick)
        .let { it.key to it.value + limiter.windowLength }
    request(limiter, key, time + weighted(0L to 3, -1L to 1, 1L to 1))
    return true
}

fun boundaryRequest(remainingRequests: Int): Boolean {
    val time = boundaryTargets.filter { it >= now }.ifEmpty { return false }.let(::pick)
    request(pick(windowLimiters), nextKey(remainingRequests), time)
    return true
}

fun burst(remainingRequests: Int): Int {
    val limiter = if (chance(0.85)) pick(windowLimiters) else pick(limiters)
    val key = nextKey(remainingRequests)
    val size = minOf(remainingRequests, 2 + random.nextInt(9))
    request(limiter, key, nextTimeArgument())
    repeat(size - 1) { request(limiter, key, minOf(MAX_TIME, now + weighted(0L to 4, 1L to 2, random.nextLong(2, 200) to 3))) }
    return size
}

fun boundaryBurst(remainingRequests: Int): Int {
    val limiter = windowLimiters.filter { it.limit <= 10 && it.windowLength in 7L..1_000_000L }.ifEmpty { return 0 }.let(::pick)
    val window = limiter.windowLength
    val boundary = (now / window + 1) * window
    val key = nextKey(remainingRequests)
    val before = minOf(remainingRequests, limiter.limit.toInt() + random.nextInt(2))
    val after = minOf(remainingRequests - before, limiter.limit.toInt() + random.nextInt(3))
    val beforeTimes = List(before) { boundary - 1 - random.nextLong(maxOf(1, window / 4)) }.sorted()
    val afterTimes = List(after) { boundary + random.nextLong(maxOf(1, window / 4)) }.sorted()
    (beforeTimes + afterTimes).forEach { request(limiter, key, it) }
    return before + after
}

fun runNormal() {
    val names = WINDOW_NAMES.shuffled(random)
    val windowCount = 2 + random.nextInt(3)
    names.take(windowCount).forEach { define(randomWindowModel(it)) }
    if (chance(0.7)) define(TokenModel(pick(OTHER_NAMES), weighted(1L to 1, 3L to 1, 10L to 1), weighted(1L to 1, 3L to 1, 1_000L to 1)))
    if (chance(0.5)) define(LeakyModel("lb", weighted(1L to 1, 2L to 1, 5L to 1), weighted(1L to 1, 100L to 1, 1_000L to 1)))
    if (chance(0.6)) reportLine()
    val emptyWindow = randomWindowModel(names[windowCount])
    if (chance(0.5)) {
        define(emptyWindow)
        emit("REPORT ${emptyWindow.name}")
    }
    var sent = 0
    var reportsLeft = options.reports
    while (sent < options.requests) {
        val remaining = options.requests - sent
        val reportWeight = if (reportsLeft > 0) maxOf(1, 100 * reportsLeft / remaining) else 0
        when (
            weighted(
                "request" to 52, "burst" to 7, "edge-burst" to 4, "echo" to 8, "boundary" to 8, "report" to reportWeight, "ghost" to 2,
                "redefine" to 1, "malformed" to 5, "blank" to 1,
            )
        ) {
            "request" -> {
                val limiter = if (chance(0.85)) pick(windowLimiters) else pick(limiters)
                request(limiter, nextKey(remaining), nextTimeArgument())
                sent++
            }
            "burst" -> sent += burst(remaining)
            "edge-burst" -> sent += boundaryBurst(remaining)
            "echo" -> if (windowEchoRequest()) sent++
            "boundary" -> if (boundaryRequest(remaining)) sent++
            "report" -> {
                reportLine()
                reportsLeft--
            }
            "ghost" -> ghostRequest()
            "redefine" -> redefine()
            "malformed" -> emit(malformedLine())
            else -> emit(pick(listOf("", "  ")))
        }
    }
    windowLimiters.forEach { emit("REPORT ${it.name}") }
}

val HOT_KEYS = listOf("hot:a", "hot:b")

fun scaleStep(): Long = weighted(0L to 4, random.nextLong(1, 5) to 4, random.nextLong(5, 21) to 2)

fun runScale() {
    val hour = WindowModel("hour", 150_000, 3_600_000)
    listOf(
        hour,
        WindowModel("odd", 5, 777),
        WindowModel("sec", 10, 1_000),
        WindowModel("burst", 20, 5_000),
        WindowModel("minute", 100, 60_000),
        WindowModel("tenmin", 400, 600_000),
        WindowModel("daily:user", 3, 3_600_000),
        TokenModel("api", 10, 1),
        LeakyModel("queue", 5, 100),
    ).forEach(::define)
    val ordinaryLimiters = limiters.filter { it !== hour }
    val hotRequests = options.requests / 2
    val ordinaryRequests = options.requests - hotRequests
    val reportEvery = maxOf(1, options.requests / maxOf(1, options.reports))
    var hotSent = 0
    var ordinarySent = 0
    while (hotSent + ordinarySent < options.requests) {
        val sent = hotSent + ordinarySent
        if (sent % reportEvery == reportEvery - 1) emit("REPORT ${pick(windowLimiters).name}")
        if (chance(0.003)) emit(malformedLine())
        val timeArgument = if (now > 0 && chance(0.01)) backwardTime() else now + scaleStep()
        val hotShare = (hotRequests - hotSent).toDouble() / (options.requests - sent)
        if (chance(hotShare)) {
            request(hour, pick(HOT_KEYS), timeArgument)
            hotSent++
        } else {
            val limiter = if (chance(0.7)) pick(ordinaryLimiters) else hour
            val key = nextKey(ordinaryRequests - ordinarySent)
            request(limiter, key, timeArgument)
            ordinarySent++
            if (chance(0.02)) {
                repeat(minOf(ordinaryRequests - ordinarySent, 2 + random.nextInt(10))) {
                    request(limiter, key, now + weighted(0L to 3, 1L to 1))
                    ordinarySent++
                }
            }
        }
    }
    windowLimiters.forEach { emit("REPORT ${it.name}") }
}

if (largeScale) runScale() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
