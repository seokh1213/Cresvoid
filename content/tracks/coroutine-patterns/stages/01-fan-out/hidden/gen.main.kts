import kotlin.random.Random

class Options(val runs: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--runs" to "60", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(runs = values.getValue("--runs").toInt(), seed = values.getValue("--seed").toLong())
}

val options = parseOptions(args)
val random = Random(options.seed)
val output = StringBuilder()

val MAX_NUMBER = 1_000_000_000L
val SERVICES = listOf("price", "stock", "review", "coupon")
val REQUIRED = listOf("price", "stock")
val STARTED_AT_ZERO = listOf("price", "stock", "review")

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

data class Reply(val latency: Long, val value: Long?)

data class Setup(val replies: Map<String, Reply>, val timeouts: Map<String, Long>, val deadline: Long)

enum class EndKind { RESPONSE, TIMEOUT, DEADLINE }

class CallEnd(val kind: EndKind, val time: Long, val response: Long, val timeoutEnd: Long, val erred: Boolean) {
    val requiredFailure: Boolean get() = kind == EndKind.TIMEOUT || (kind == EndKind.RESPONSE && erred)
}

fun Setup.callEnd(service: String, start: Long): CallEnd {
    val reply = replies.getValue(service)
    val response = start + reply.latency
    val timeoutEnd = start + timeouts.getValue(service)
    val erred = reply.value == null
    return when {
        response < timeoutEnd && response < deadline -> CallEnd(EndKind.RESPONSE, response, response, timeoutEnd, erred)
        timeoutEnd < deadline -> CallEnd(EndKind.TIMEOUT, timeoutEnd, response, timeoutEnd, erred)
        else -> CallEnd(EndKind.DEADLINE, deadline, response, timeoutEnd, erred)
    }
}

// 스펙 5.6, 5.7 이 출력을 정하지 않는 겹침이 없는지 본다.
fun Setup.isDefined(): Boolean {
    if (STARTED_AT_ZERO.any { timeouts.getValue(it) == deadline }) return false
    val ends = STARTED_AT_ZERO.associateWith { callEnd(it, 0) }.toMutableMap()
    val failureTime = REQUIRED.map { ends.getValue(it) }.filter { it.requiredFailure }.minOfOrNull { it.time }
    val price = ends.getValue("price")
    val priceAnswered = price.kind == EndKind.RESPONSE && !price.erred
    if (priceAnswered && price.time == failureTime) return false
    if (priceAnswered && (failureTime == null || price.time < failureTime)) {
        if (price.time + timeouts.getValue("coupon") == deadline) return false
        ends["coupon"] = callEnd("coupon", price.time)
    }
    if (failureTime == null) return true
    val failing = REQUIRED.filter { ends.getValue(it).let { end -> end.requiredFailure && end.time == failureTime } }
    if (failing.size > 1) return false
    return ends.none { (service, end) ->
        service !in failing && end.time >= failureTime && (end.response == failureTime || end.timeoutEnd == failureTime)
    }
}

fun valueFor(service: String): Long = when (service) {
    "price" -> weighted({ between(100, 200_000) } to 8, { 0L } to 1, { MAX_NUMBER } to 1, { between(1, 99) } to 1)()
    "stock" -> weighted({ between(0, 500) } to 8, { 0L } to 2, { MAX_NUMBER } to 1)()
    "review" -> weighted({ between(0, 5_000) } to 8, { 0L } to 1, { MAX_NUMBER } to 1)()
    else -> weighted({ between(0, 5_000) } to 8, { 0L } to 2, { MAX_NUMBER } to 1)()
}

fun errorRate(service: String): Double = if (service in REQUIRED) 0.12 else 0.22

class Scale(val span: Long, val anchors: List<Long>) {
    fun time(): Long = weighted(
        { between(1, span) } to 7,
        { pick(anchors) } to 3,
        { 1L } to 1,
        { between(1, MAX_NUMBER) } to 1,
    )()
}

fun randomScale(): Scale {
    val span = weighted(300L to 10, 1_000L to 4, 60L to 3, 1_000_000L to 1, 400_000_000L to 1)
    return Scale(span, List(3) { between(1, span) })
}

fun randomReply(service: String, scale: Scale): Reply =
    Reply(latency = scale.time(), value = if (chance(errorRate(service))) null else valueFor(service))

fun timeoutFor(service: String, latency: Long, scale: Scale): Long = weighted(
    { latency + between(1, scale.span) } to if (service in REQUIRED) 16 else 6,
    { latency } to 2,
    { if (latency > 1) between(1, latency - 1) else latency + 1 } to 2,
    { pick(scale.anchors) } to 1,
    { scale.time() } to 1,
)().coerceIn(1, MAX_NUMBER)

fun deadlineFor(replies: Map<String, Reply>, timeouts: Map<String, Long>, scale: Scale): Long {
    val price = replies.getValue("price").latency
    val couponResponse = price + replies.getValue("coupon").latency
    val requiredResponse = maxOf(price, replies.getValue("stock").latency)
    val latest = maxOf(couponResponse, replies.getValue("review").latency, requiredResponse)
    val responses = listOf(price, replies.getValue("stock").latency, replies.getValue("review").latency, couponResponse)
    return weighted(
        { latest + between(1, scale.span) } to 5,
        { requiredResponse + between(1, maxOf(1, latest - requiredResponse)) } to 4,
        { between(1, maxOf(1, requiredResponse)) } to 2,
        { pick(responses) } to 1,
        { pick(timeouts.values.toList()) + between(-2, 2) } to 1,
        { scale.time() } to 1,
    )().coerceIn(1, MAX_NUMBER)
}

fun freshSetup(): Setup {
    while (true) {
        val scale = randomScale()
        val replies = SERVICES.associateWith { randomReply(it, scale) }
        val timeouts = SERVICES.associateWith { timeoutFor(it, replies.getValue(it).latency, scale) }
        val setup = Setup(replies, timeouts, deadlineFor(replies, timeouts, scale))
        if (setup.isDefined()) return setup
    }
}

fun changedSetup(previous: Setup): Setup? {
    repeat(60) {
        val scale = randomScale()
        var replies = previous.replies
        var timeouts = previous.timeouts
        var deadline = previous.deadline
        repeat(random.nextInt(1, 4)) {
            val service = pick(SERVICES)
            when (random.nextInt(3)) {
                0 -> replies = replies + (service to randomReply(service, scale))
                1 -> timeouts = timeouts + (service to timeoutFor(service, replies.getValue(service).latency, scale))
                else -> deadline = deadlineFor(replies, timeouts, scale)
            }
        }
        val setup = Setup(replies, timeouts, deadline)
        if (setup != previous && setup.isDefined()) return setup
    }
    return null
}

fun callLine(service: String, reply: Reply): String =
    if (reply.value == null) "CALL $service ${reply.latency} ERROR" else "CALL $service ${reply.latency} OK ${reply.value}"

fun settingLines(setup: Setup): Map<String, String> =
    SERVICES.associate { "call $it" to callLine(it, setup.replies.getValue(it)) } +
        SERVICES.associate { "timeout $it" to "TIMEOUT $it ${setup.timeouts.getValue(it)}" } +
        mapOf("deadline" to "DEADLINE ${setup.deadline}")

fun decoyFor(key: String): String {
    val service = key.substringAfter(' ')
    return when {
        key.startsWith("call") -> callLine(service, randomReply(service, randomScale()))
        key.startsWith("timeout") -> "TIMEOUT $service ${between(1, 500)}"
        else -> "DEADLINE ${between(1, 500)}"
    }
}

fun spaced(line: String): String =
    if (chance(0.04)) " ".repeat(random.nextInt(0, 3)) + line.split(' ').joinToString(" ".repeat(random.nextInt(2, 4))) + "  " else line

fun emitSettings(previous: Setup?, setup: Setup) {
    val lines = settingLines(setup)
    val before = previous?.let(::settingLines).orEmpty()
    val changed = lines.filter { (key, line) -> before[key] != line }.keys.toMutableList()
    if (previous != null && chance(0.2)) changed += pick(lines.keys.toList())
    for (key in changed.distinct().shuffled(random)) {
        if (chance(0.06)) emit(spaced(decoyFor(key)))
        emit(spaced(lines.getValue(key)))
    }
}

var current: Setup? = null
var traceOn = true
repeat(options.runs) {
    val previous = current
    val next = if (previous == null || chance(0.6)) freshSetup() else changedSetup(previous) ?: freshSetup()
    val switchTrace = if (traceOn) chance(0.08) else chance(0.4)
    if (switchTrace) traceOn = !traceOn
    if (switchTrace || chance(0.02)) emit(if (traceOn) "TRACE ON" else "TRACE OFF")
    emitSettings(previous, next)
    if (chance(0.03)) emit(pick(listOf("", "   ")))
    emit(spaced("DETAIL"))
    current = next
}

System.out.write(output.toString().encodeToByteArray())
System.out.flush()
