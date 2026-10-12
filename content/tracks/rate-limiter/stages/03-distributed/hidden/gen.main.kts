import kotlin.random.Random

class Options(val keys: Int, val calls: Int, val gateways: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--keys" to "20", "--calls" to "300", "--gateways" to "3", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        keys = values.getValue("--keys").toInt(),
        calls = values.getValue("--calls").toInt(),
        gateways = values.getValue("--gateways").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.calls >= 100_000
val output = StringBuilder()

val MAX_TIME = 1_000_000_000_000L
val PRIORITIES = listOf("critical", "post", "get", "test")

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

class GatewayModel(val name: String, val delay: Long, val workers: Long, val processingTime: Long) {
    val definition = "GATEWAY $name $delay $workers $processingTime"
}

sealed class LimiterModel(val name: String) {
    abstract val definition: String
    open val shared: Boolean = true
}

class ReadWriteModel(name: String, limit: Long, windowLength: Long) : LimiterModel(name) {
    override val definition = "RW $name $limit $windowLength"
}

class AtomicModel(name: String, limit: Long, windowLength: Long) : LimiterModel(name) {
    override val definition = "ATOMIC $name $limit $windowLength"
}

class LocalCopyModel(name: String, limit: Long, windowLength: Long, syncPeriod: Long) : LimiterModel(name) {
    override val definition = "LOCAL $name $limit $windowLength $syncPeriod"
}

class StandaloneModel(name: String, override val definition: String, val window: Boolean) : LimiterModel(name) {
    override val shared = false
}

val UNKNOWN_COMMANDS = listOf(
    "call", "Call", "CALLS", "gateway", "Gateway", "GW", "peek", "Peek", "down", "Up", "DOWN_", "UPS", "rw", "Rw",
    "atomic", "local", "LOCALS", "INCR", "GET", "SET", "ADD", "SHED", "OPEN", "req",
)
val BAD_NAMES = listOf("x".repeat(65), "g/1", "게이트", "a*b", "user@1", "é", "#")
val BAD_NUMBERS = listOf("01", "-1", "+5", "1.0", "1e3", "00", "99999999999999999999999", "١٢")
val BAD_PRIORITIES = listOf("GET", "Critical", "CRITICAL", "none", "high", "post,get", "tests", "0")
val SPECIAL_KEYS = listOf("a", "Z", "_", "-", ".", ":", "Alice", "alice", "k".repeat(64), "0")
val GATEWAY_NAMES = listOf("g1", "g2", "edge-a", "edge.b", "api", "gw:3", "Z", "w", "slow", "g".repeat(64))
val SHARED_NAMES = listOf("api", "rw", "cnt", "lim", "atomic.1", "local:2", "L", "pay-rw", "s", "hold")
val GHOST_NAMES = listOf("ghost", "API", "nope", "Rw", "none")

val gateways = ArrayList<GatewayModel>()
val gatewayNames = HashSet<String>()
val limiters = ArrayList<LimiterModel>()
val defined = HashMap<String, LimiterModel>()
val interestingTimes = ArrayList<Long>()
var now = 0L
var storeRunning = true
var nextFreshKey = 0
val usedKeys = ArrayList<String>()

val sharedLimiters: List<LimiterModel> get() = limiters.filter { it.shared }
val standaloneLimiters: List<LimiterModel> get() = limiters.filter { !it.shared }

fun keyName(index: Int): String = when (index % 5) {
    0 -> "user:$index"
    1 -> "10.${index / 65_536 % 256}.${index / 256 % 256}.${index % 256}"
    2 -> "k$index"
    3 -> "sess_${index.toString(36)}"
    else -> "api-key.$index"
}

fun nextKey(remainingCalls: Int): String {
    if (!largeScale && chance(0.03)) return pick(SPECIAL_KEYS)
    val freshProbability = if (remainingCalls <= 0) 0.0 else ((options.keys - nextFreshKey).toDouble() / remainingCalls).coerceIn(0.0, 0.9)
    if (usedKeys.isEmpty() || (nextFreshKey < options.keys && chance(freshProbability))) {
        val key = keyName(nextFreshKey++)
        usedKeys += key
        return key
    }
    val skewed = (usedKeys.size * Math.pow(random.nextDouble(), 3.0)).toInt().coerceIn(0, usedKeys.size - 1)
    return usedKeys[skewed]
}

fun defineGateway(gateway: GatewayModel) {
    emit(gateway.definition)
    if (gatewayNames.add(gateway.name)) gateways += gateway
}

fun defineLimiter(limiter: LimiterModel) {
    emit(limiter.definition)
    if (limiter.name !in defined) {
        defined[limiter.name] = limiter
        limiters += limiter
    }
}

fun randomGateway(name: String): GatewayModel = when (weighted("fast" to 4, "far" to 2, "busy" to 1, "wide" to 1)) {
    "fast" -> GatewayModel(
        name,
        delay = weighted(0L to 2, 1L to 2, 5L to 3, 10L to 3),
        workers = weighted(2L to 2, 3L to 2, 5L to 3, 10L to 3),
        processingTime = weighted(0L to 4, 1L to 1, 10L to 2),
    )
    "far" -> GatewayModel(
        name,
        delay = weighted(50L to 3, 120L to 1, 500L to 1),
        workers = weighted(10L to 2, 50L to 1, 1_000_000L to 1),
        processingTime = weighted(0L to 3, 10L to 1, 100L to 1),
    )
    "busy" -> GatewayModel(
        name,
        delay = weighted(1L to 1, 5L to 1),
        workers = weighted(1L to 1, 2L to 1, 5L to 1),
        processingTime = weighted(100L to 2, 1_000L to 1),
    )
    else -> GatewayModel(name, delay = weighted(0L to 1, 10L to 1), workers = 1_000_000L, processingTime = weighted(0L to 1, 100L to 1))
}

fun randomLimit(): Long = weighted(1L to 3, 2L to 3, 3L to 3, 5L to 2, 10L to 2, 100L to 1, 1_000_000_000L to 1)

fun randomWindow(): Long = weighted(10L to 1, 100L to 3, 777L to 2, 1_000L to 4, 60_000L to 2, 1_000_000_000L to 1)

fun randomSharedLimiter(name: String): LimiterModel = when (random.nextInt(3)) {
    0 -> ReadWriteModel(name, randomLimit(), randomWindow())
    1 -> AtomicModel(name, randomLimit(), randomWindow())
    else -> LocalCopyModel(name, randomLimit(), randomWindow(), weighted(0L to 2, 1L to 1, 50L to 2, 100L to 2, 1_000L to 1, 1_000_000_000L to 1))
}

fun pad(line: String): String = if (chance(0.02)) "  " + line.replace(" ", "   ") + " " else line

fun advance(timeArgument: Long) {
    now = maxOf(now, timeArgument)
}

fun remember(time: Long) {
    if (largeScale) return
    interestingTimes += time
    if (interestingTimes.size > 64) interestingTimes.subList(0, interestingTimes.size - 64).clear()
}

fun call(limiter: LimiterModel, key: String, gateway: GatewayModel, priority: String, timeArgument: Long) {
    emit(pad("CALL ${limiter.name} $key $timeArgument ${gateway.name} $priority"))
    advance(timeArgument)
    remember(now + gateway.delay)
    remember(now + 2 * gateway.delay)
    remember(now + 2 * gateway.delay + gateway.processingTime)
    remember(now + gateway.processingTime)
}

fun backwardTime(): Long = now - 1 - random.nextLong(minOf(now, if (chance(0.1)) now else 300L))

fun stepFromNow(): Long = weighted(
    { 0L } to 35,
    { random.nextLong(1, 6) } to 25,
    { random.nextLong(6, 51) } to 20,
    { random.nextLong(51, 501) } to 14,
    { random.nextLong(501, 5_001) } to 5,
    { random.nextLong(5_001, 2_000_000) } to 1,
)()

fun nextTimeArgument(): Long = minOf(
    MAX_TIME,
    when {
        now > 0 && chance(0.03) -> backwardTime()
        chance(0.15) && interestingTimes.any { it >= now } -> pick(interestingTimes.filter { it >= now })
        else -> now + stepFromNow()
    },
)

fun priority(): String = weighted("critical" to 2, "post" to 3, "get" to 4, "test" to 3)

fun singleCall(remainingCalls: Int) {
    call(pick(sharedLimiters), nextKey(remainingCalls), pick(gateways), priority(), nextTimeArgument())
}

fun contention(remainingCalls: Int): Int {
    val limiter = pick(sharedLimiters)
    val key = nextKey(remainingCalls)
    val size = minOf(remainingCalls, 2 + random.nextInt(6))
    val time = nextTimeArgument()
    repeat(size) { index ->
        val timeArgument = if (index == 0 || chance(0.6)) time else now + random.nextLong(0, 15)
        call(limiter, key, pick(gateways), priority(), timeArgument)
    }
    return size
}

fun crowd(remainingCalls: Int): Int {
    val gateway = gateways.filter { it.workers <= 10 }.ifEmpty { return 0 }.let(::pick)
    val size = minOf(remainingCalls, gateway.workers.toInt() + 1 + random.nextInt(4))
    val time = nextTimeArgument()
    repeat(size) { call(pick(sharedLimiters), nextKey(remainingCalls), gateway, priority(), if (chance(0.8)) time else now) }
    return size
}

fun peek() {
    val limiter = if (chance(0.9)) pick(sharedLimiters) else pick(standaloneLimiters.ifEmpty { sharedLimiters })
    val key = if (usedKeys.isEmpty() || chance(0.05)) pick(SPECIAL_KEYS) else pick(usedKeys)
    val timeArgument = nextTimeArgument()
    emit(pad("PEEK ${limiter.name} $key $timeArgument"))
    if (limiter.shared) advance(timeArgument)
}

var outageEndsAfter = -1

fun toggleStore(sent: Int) {
    val timeArgument = nextTimeArgument()
    val redundant = chance(0.2)
    val stopping = if (redundant) !storeRunning else storeRunning
    emit(if (stopping) "DOWN $timeArgument" else "UP $timeArgument")
    storeRunning = !stopping
    outageEndsAfter = if (storeRunning) -1 else sent + 2 + random.nextInt(12)
    advance(timeArgument)
}

fun standaloneRequest() {
    val limiter = pick(standaloneLimiters.ifEmpty { return })
    if (limiter is StandaloneModel && limiter.window && chance(0.3)) {
        emit("REPORT ${limiter.name}")
        return
    }
    val key = if (usedKeys.isEmpty()) "k0" else pick(usedKeys)
    val timeArgument = nextTimeArgument()
    emit("REQ ${limiter.name} $key $timeArgument")
    advance(timeArgument)
}

fun kindMismatch() {
    val key = if (usedKeys.isEmpty()) "k0" else pick(usedKeys)
    val line = weighted(
        { "REQ ${pick(sharedLimiters).name} $key $now" } to 2,
        { "REPORT ${pick(sharedLimiters).name}" } to 1,
        { standaloneLimiters.ifEmpty { null }?.let { "CALL ${pick(it).name} $key $now ${pick(gateways).name} get" } } to 2,
        { standaloneLimiters.ifEmpty { null }?.let { "PEEK ${pick(it).name} $key $now" } } to 1,
        { "CALL ${pick(GHOST_NAMES.filter { it !in defined })} $key $now ${pick(GHOST_NAMES)} get" } to 1,
        { "CALL ${pick(sharedLimiters).name} $key $now ${pick(GHOST_NAMES.filter { it !in gatewayNames })} post" } to 2,
        { "PEEK ${pick(GHOST_NAMES.filter { it !in defined })} $key $now" } to 1,
    )() ?: return
    emit(line)
}

fun malformedLine(): String {
    val limiter = pick(sharedLimiters).name
    val key = if (usedKeys.isEmpty()) "k0" else pick(usedKeys)
    val gateway = pick(gateways).name
    val name = pick(SHARED_NAMES + GATEWAY_NAMES)
    return weighted(
        { "${pick(UNKNOWN_COMMANDS)} $limiter $key $now $gateway get" } to 3,
        { pick(UNKNOWN_COMMANDS) } to 1,
        { "CALL $limiter $key $now $gateway" } to 2,
        { "CALL $limiter $key $now $gateway get extra" } to 1,
        { "CALL $limiter $key $now $gateway ${pick(BAD_PRIORITIES)}" } to 3,
        { "CALL $limiter $key ${pick(BAD_NUMBERS)} $gateway get" } to 2,
        { "CALL $limiter $key $now ${pick(BAD_NAMES)} get" } to 1,
        { "CALL $limiter ${pick(BAD_NAMES)} $now $gateway get" } to 1,
        { "PEEK $limiter $key" } to 1,
        { "PEEK $limiter $key ${pick(BAD_NUMBERS)}" } to 1,
        { pick(listOf("DOWN", "UP")) } to 1,
        { "${pick(listOf("DOWN", "UP"))} $now $now" } to 1,
        { "${pick(listOf("DOWN", "UP"))} ${pick(BAD_NUMBERS)}" } to 1,
        { "GATEWAY $name ${pick(listOf("1000001", "-1", "01"))} 5 5" } to 1,
        { "GATEWAY $name 5 ${pick(listOf("0", "1000001"))} 5" } to 1,
        { "GATEWAY $name 5 5 ${pick(listOf("1000001", "1.5"))}" } to 1,
        { "GATEWAY $name 5 5" } to 1,
        { "${pick(listOf("RW", "ATOMIC"))} $name ${pick(listOf("0", "1000000001"))} 1000" } to 1,
        { "${pick(listOf("RW", "ATOMIC"))} $name 5 ${pick(listOf("0", "1000000001"))}" } to 1,
        { "${pick(listOf("RW", "ATOMIC"))} $name 5 1000 100" } to 1,
        { "LOCAL $name 5 1000" } to 1,
        { "LOCAL $name 5 1000 ${pick(listOf("1000000001", "-1", "01"))}" } to 1,
        { "CALL $limiter $key $now $gateway get\r" } to 1,
        { "UP\t$now" } to 1,
    )()
}

fun duplicateDefinition() {
    if (chance(0.5)) {
        val existing = pick(gateways)
        emit(randomGateway(existing.name).definition)
    } else {
        val existing = pick(limiters)
        emit(randomSharedLimiter(existing.name).definition)
    }
}

fun runNormal() {
    GATEWAY_NAMES.shuffled(random).take(maxOf(1, options.gateways)).forEach { defineGateway(randomGateway(it)) }
    val sharedNames = SHARED_NAMES.shuffled(random)
    val sharedCount = 2 + random.nextInt(3)
    sharedNames.take(sharedCount).forEach { defineLimiter(randomSharedLimiter(it)) }
    if (chance(0.6)) defineLimiter(StandaloneModel("tb", "TOKEN tb ${randomLimit()} ${weighted(1L to 1, 3L to 1)}", window = false))
    if (chance(0.6)) defineLimiter(StandaloneModel("win", "WINDOW win ${randomLimit()} ${randomWindow()}", window = true))
    val lateLimiter = randomSharedLimiter(sharedNames[sharedCount])
    val lateGateway = GATEWAY_NAMES.firstOrNull { it !in gatewayNames }?.let(::randomGateway)
    var sent = 0
    while (sent < options.calls) {
        val remaining = options.calls - sent
        if (!storeRunning && sent >= outageEndsAfter) toggleStore(sent)
        if (sent == options.calls / 2) {
            defineLimiter(lateLimiter)
            lateGateway?.let(::defineGateway)
        }
        when (
            weighted(
                "call" to 50, "contention" to 8, "crowd" to 4, "peek" to 8, "store" to 3, "standalone" to 2,
                "mismatch" to 3, "duplicate" to 1, "malformed" to 5, "blank" to 1,
            )
        ) {
            "call" -> {
                singleCall(remaining)
                sent++
            }
            "contention" -> sent += contention(remaining)
            "crowd" -> sent += crowd(remaining)
            "peek" -> peek()
            "store" -> toggleStore(sent)
            "standalone" -> standaloneRequest()
            "mismatch" -> kindMismatch()
            "duplicate" -> duplicateDefinition()
            "malformed" -> emit(malformedLine())
            else -> emit(pick(listOf("", "   ")))
        }
    }
    if (!storeRunning && chance(0.5)) emit("UP ${now + random.nextLong(0, 100)}")
    repeat(random.nextInt(3)) { peek() }
}

fun scaleGateway(index: Int): GatewayModel = GatewayModel(
    "gw$index",
    delay = if (index == 0) 1 else if (index == 1) 500 else random.nextLong(1, 501),
    workers = weighted(800L to 2, 2_000L to 3, 4_000L to 3, 1_000_000L to 1),
    processingTime = if (index == 2) 2_000 else weighted(0L to 2, random.nextLong(1, 200) to 3, random.nextLong(200, 2_001) to 3),
)

fun runScale() {
    repeat(options.gateways) { defineGateway(scaleGateway(it)) }
    listOf(
        ReadWriteModel("rw", 5, 1_000),
        ReadWriteModel("rw:hour", 1_000, 3_600_000),
        AtomicModel("atomic", 5, 1_000),
        AtomicModel("atomic:777", 3, 777),
        LocalCopyModel("local", 5, 1_000, 100),
        LocalCopyModel("local:eager", 20, 10_000, 0),
        LocalCopyModel("local:lazy", 50, 60_000, 5_000),
    ).forEach(::defineLimiter)
    val shared = sharedLimiters
    val hotKeys = List(40) { "hot:$it" }
    var sent = 0
    var nextOutageAt = random.nextInt(20_000, 60_000)
    while (sent < options.calls) {
        if (sent == nextOutageAt) {
            emit("DOWN $now")
            storeRunning = false
        } else if (!storeRunning && sent == nextOutageAt + 4_000) {
            emit("UP ${now + random.nextLong(0, 3)}")
            storeRunning = true
            nextOutageAt = sent + random.nextInt(40_000, 120_000)
        }
        when {
            chance(0.01) -> emit("PEEK ${pick(shared).name} ${if (chance(0.5)) pick(hotKeys) else pick(usedKeys.ifEmpty { hotKeys })} $now")
            chance(0.002) -> emit(malformedLine())
            chance(0.0005) -> emit("${if (storeRunning) "UP" else "DOWN"} $now")
        }
        val timeArgument = if (now > 0 && chance(0.01)) backwardTime() else now + weighted(0L to 19, 1L to 1)
        val key = if (chance(0.15)) pick(hotKeys) else nextKey(options.calls - sent)
        call(pick(shared), key, pick(gateways), priority(), timeArgument)
        sent++
    }
    if (!storeRunning) emit("UP $now")
}

if (largeScale) runScale() else runNormal()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
