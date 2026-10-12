import kotlin.random.Random

class Options(val runs: Int, val events: Int, val subscribers: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--runs" to "6", "--events" to "40", "--subscribers" to "6", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        runs = values.getValue("--runs").toInt(),
        events = values.getValue("--events").toInt(),
        subscribers = values.getValue("--subscribers").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.events >= 1_000
val output = StringBuilder()

val MAX_PRICE = 10_000_000L
val PRODUCTS = listOf("p1", "p2", "sku-7", "tv-55", "a", "p10", "shoe-270", "0", "z9-mug-ceramic-large")

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

enum class Kind { WATCH, AUDIT, MONITOR }

class Subscriber(val kind: Kind, val name: String, val from: Long, val until: Long, val work: Long, val capacity: Int) {
    val line: String
        get() = when (kind) {
            Kind.AUDIT -> "AUDIT $name $from $until $work $capacity"
            else -> "${kind.name} $name $from $until $work"
        }

    fun startingAt(time: Long) = Subscriber(kind, name, time, until, work, capacity)
}

// 이벤트 간격과 처리 시간을 같은 눈금으로 재야 처리를 마치는 시각과 이벤트 시각이 자주 겹친다.
class Timing(val grid: Long) {
    fun step(): Long = grid * weighted({ 1L } to 4, { between(2, 4) } to 4, { between(5, 20) } to 1)()

    fun work(kind: Kind): Long = grid * when (kind) {
        Kind.WATCH -> weighted({ 1L } to 3, { between(2, 6) } to 4, { between(7, 30) } to 2)()
        Kind.AUDIT, Kind.MONITOR -> weighted({ 1L } to 1, { between(2, 6) } to 3, { between(7, 25) } to 3)()
    }

    fun onGrid(time: Long): Long = time / grid * grid
}

// 스펙 4.12 대로 WATCH 수집자 수가 0 이 된 뒤 멈춤 대기가 지나 공유 구독이 끝나는 시각을 모두 구한다.
fun sharedEndTimes(subscribers: List<Subscriber>, stopTimeout: Long): List<Long> {
    val changes = subscribers.filter { it.kind == Kind.WATCH }
        .flatMap { listOf(it.from to 1, it.until to -1) }
        .sortedBy { it.first }
    val ends = mutableListOf<Long>()
    var watching = 0
    var endsAt: Long? = null
    for ((time, delta) in changes) {
        val pendingEnd = endsAt
        if (delta > 0 && pendingEnd != null) {
            if (time >= pendingEnd) ends += pendingEnd
            endsAt = null
        }
        watching += delta
        if (watching == 0) endsAt = time + stopTimeout
    }
    endsAt?.let { ends += it }
    return ends
}

class Plan(val subscribers: List<Subscriber>, val stopTimeout: Long, val forbidden: Set<Long>)

fun freeTime(used: MutableSet<Long>, wanted: Long): Long {
    var time = wanted
    while (time in used) time++
    used += time
    return time
}

fun subscriberName(kind: Kind, index: Int): String = when (kind) {
    Kind.WATCH -> weighted("w$index" to 4, "screen-$index" to 2, "watch$index" to 1)
    Kind.AUDIT -> weighted("a$index" to 4, "audit-$index" to 2, "ledger$index" to 1)
    Kind.MONITOR -> weighted("m$index" to 4, "monitor-$index" to 2, "alert$index" to 1)
}

fun randomCapacity(): Int = weighted(0 to 3, 1 to 3, 2 to 2, random.nextInt(3, 11) to 1, 100_000 to 1)

fun randomStopTimeout(runIndex: Int): Long =
    if (largeScale) {
        if (runIndex % 2 == 0) weighted({ 0L } to 1, { between(1, 50) } to 1)() else between(51, 600)
    } else {
        weighted({ 0L } to 3, { between(1, 20) } to 2, { between(21, 300) } to 3)()
    }

fun randomPlan(runIndex: Int, horizon: Long, timing: Timing): Plan {
    val stopTimeout = randomStopTimeout(runIndex)
    val count = when {
        largeScale -> options.subscribers + random.nextInt(-2, 3)
        else -> weighted(1 to 1, random.nextInt(2, options.subscribers + 2) to 6)
    }
    val used = HashSet<Long>()
    val subscribers = ArrayList<Subscriber>()
    fun add(kind: Kind, wantedFrom: Long, length: Long) {
        val from = freeTime(used, timing.onGrid(wantedFrom))
        val until = freeTime(used, maxOf(from + 1, minOf(from + timing.onGrid(length), horizon)))
        val capacity = if (kind == Kind.AUDIT) randomCapacity() else 0
        val name = subscriberName(kind, subscribers.size)
        val drawnWork = timing.work(kind)
        // 스펙 5.2 넷째 조건: 시각 0 에 시작한 WATCH 수집자가 첫 처리를 끝 시각에 마치지 않게 한다.
        val work = if (kind == Kind.WATCH && from == 0L && drawnWork == until) drawnWork + 1 else drawnWork
        subscribers += Subscriber(kind, name, from, until, work, capacity)
    }
    while (subscribers.size < count) {
        val kind = when {
            subscribers.isEmpty() && chance(0.6) -> Kind.WATCH
            else -> weighted(Kind.WATCH to 4, Kind.AUDIT to 3, Kind.MONITOR to 3)
        }
        val wantedFrom = weighted({ between(0, horizon) } to 8, { 0L } to 1, { between(0, horizon / 10) } to 1)()
        val longLived = if (kind == Kind.WATCH) 0 else 4
        add(
            kind,
            wantedFrom = wantedFrom,
            length = weighted(
                { timing.grid * between(1, 6) } to 2,
                { between(61, horizon / 3 + 61) } to 5,
                { between(horizon / 3, horizon) } to longLived,
                { maxOf(1L, horizon - wantedFrom) } to 2,
            )(),
        )
        val previous = subscribers.last()
        if (previous.kind == Kind.WATCH && subscribers.size < count && chance(0.5)) {
            val pause = weighted(
                { between(1, maxOf(1, stopTimeout - 1)) } to 2,
                { stopTimeout + between(1, 40) } to 2,
                { -between(1, previous.until - previous.from) } to 1,
            )()
            add(
                Kind.WATCH,
                wantedFrom = maxOf(0, previous.until + pause),
                length = weighted({ timing.grid * between(1, 6) } to 1, { between(61, horizon / 3 + 61) } to 1)(),
            )
        }
    }
    return settle(subscribers, stopTimeout)
}

// 스펙 5.2: 공유 구독이 끝나는 시각에 WATCH 수집자가 시작하지 않도록 그 수집자의 시작을 미룬다.
fun settle(subscribers: List<Subscriber>, stopTimeout: Long): Plan {
    var settled = subscribers
    while (true) {
        val ends = sharedEndTimes(settled, stopTimeout).toSet()
        val clash = settled.firstOrNull { it.kind == Kind.WATCH && it.from in ends } ?: return Plan(settled, stopTimeout, ends)
        val used = settled.flatMap { listOf(it.from, it.until) }.toMutableSet()
        val moved = (clash.from + 1..<clash.until).firstOrNull { it !in used }
        settled = if (moved == null) settled - clash else settled.map { if (it === clash) it.startingAt(moved) else it }
    }
}

// 정가와 할인율을 몇 개의 값 안에서 골라야 같은 판매가가 다시 나와 감시 수집자가 같은 값을 다시 받는다.
class PriceStyle(val listPrices: List<Long>, val percents: List<Long>)

fun randomStyle(): PriceStyle {
    val base = weighted({ between(1_000, 200_000) } to 6, { between(1, 999) } to 1, { MAX_PRICE - between(0, 9) } to 1)()
    val listPrices = List(weighted(1 to 1, 2 to 2, 3 to 2)) {
        weighted(
            { base } to 2,
            { (base / 10 * 10 + between(0, 9)).coerceIn(1, MAX_PRICE) } to 2,
            { between(1_000, 200_000) } to 2,
        )()
    }
    val percents = List(random.nextInt(2, 5)) {
        weighted({ between(0, 90) } to 3, { pick(listOf(0L, 10L, 15L, 30L, 50L, 90L)) } to 3)()
    }
    return PriceStyle(listPrices.distinct(), percents.distinct())
}

class Book(val style: PriceStyle) {
    var version = 0L
    var percent = 0L

    fun nextVersion(): Long = version + weighted(1L to 8, between(2, 4) to 2)
}

fun nextChange(book: Book): String =
    if (chance(0.4)) {
        val price = weighted(
            { pick(book.style.listPrices) } to 7,
            { between(1_000, 200_000) } to 1,
            { 1L } to 1,
            { MAX_PRICE } to 1,
        )()
        "LIST $price"
    } else {
        val percent = weighted({ pick(book.style.percents) } to 7, { between(0, 90) } to 2, { 0L } to 1, { 90L } to 1)()
        "SALE $percent"
    }

fun randomEvent(book: Book): String {
    val change = nextChange(book)
    if (book.version > 0 && chance(0.15)) return "${between(1, book.version)} $change"
    book.version = book.nextVersion()
    if (change.startsWith("SALE")) book.percent = change.substringAfter(' ').toLong()
    return "${book.version} $change"
}

// 할인율을 두 값 사이에서 짧게 오가게 해야 느린 감사 수집자의 버퍼가 차고 감시 수집자가 같은 값을 다시 받는다.
fun toggleBurst(book: Book): List<String> {
    val original = book.percent
    val toggled = pick(book.style.percents.filter { it != original }.ifEmpty { listOf((original + 10) % 91) })
    return List(weighted(2 to 3, 3 to 1, 4 to 2)) { index ->
        book.version = book.nextVersion()
        book.percent = if (index % 2 == 0) toggled else original
        "${book.version} SALE ${book.percent}"
    }
}

fun emitEvents(product: String, others: List<String>, plan: Plan, count: Int, timing: Timing) {
    val books = (others + product).associateWith { Book(randomStyle()) }
    val magnets = plan.subscribers.flatMap { listOf(it.from, it.until) }.filter { it > 0 }.sorted()
    val lastEnd = plan.subscribers.maxOfOrNull { it.until } ?: 0L
    var time = 0L
    var emitted = 0
    fun emitAfter(step: Long, eventProduct: String, versionAndChange: String) {
        var next = time + step
        while (next in plan.forbidden) next++
        time = next
        emit(spaced("EVENT $time $eventProduct $versionAndChange"))
        emitted++
    }
    while (emitted < count || time <= lastEnd + plan.stopTimeout) {
        if (chance(0.1)) {
            toggleBurst(books.getValue(product)).forEach { emitAfter(timing.grid, product, it) }
            continue
        }
        val step = timing.step()
        val magnet = magnets.firstOrNull { it > time && it <= time + step }
        val eventProduct = if (others.isEmpty() || chance(0.75)) product else pick(others)
        emitAfter(if (magnet != null && chance(0.3)) magnet - time else step, eventProduct, randomEvent(books.getValue(eventProduct)))
    }
}

fun spaced(line: String): String =
    if (!largeScale && chance(0.03)) "  " + line.split(' ').joinToString("   ") + " " else line

var traceOn = true
repeat(options.runs) { runIndex ->
    val product = pick(PRODUCTS)
    val others = (PRODUCTS - product).shuffled(random).take(random.nextInt(if (largeScale) 1 else 0, 4))
    val eventCount = when {
        largeScale -> options.events + random.nextInt(-options.events / 10, options.events / 10 + 1)
        else -> weighted(random.nextInt(options.events / 2, options.events + 1) to 6, random.nextInt(1, 4) to 1)
    }
    val timing = Timing(grid = weighted(1L to 2, 5L to 3, 10L to 3))
    val horizon = maxOf(20L, eventCount * timing.grid * 5 / 2)
    val plan = randomPlan(runIndex, horizon, timing)
    emitEvents(product, others, plan, eventCount, timing)
    plan.subscribers.shuffled(random).forEach { emit(spaced(it.line)) }
    if (!largeScale) {
        val switchTrace = if (traceOn) chance(0.15) else chance(0.5)
        if (switchTrace) traceOn = !traceOn
        if (switchTrace) emit(if (traceOn) "TRACE ON" else "TRACE OFF")
    }
    val initial = weighted({ between(1_000, 200_000) } to 6, { 0L } to 1, { MAX_PRICE } to 1)()
    emit(spaced("PRICES $product $initial ${plan.stopTimeout}"))
}

System.out.write(output.toString().encodeToByteArray())
System.out.flush()
