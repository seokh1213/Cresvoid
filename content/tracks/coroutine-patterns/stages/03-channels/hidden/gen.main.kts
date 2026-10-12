import java.util.PriorityQueue
import kotlin.random.Random

class Options(val pipelines: Int, val items: Int, val heavyItems: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--pipelines" to "10", "--items" to "6", "--heavy" to "0", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        pipelines = values.getValue("--pipelines").toInt(),
        items = values.getValue("--items").toInt(),
        heavyItems = values.getValue("--heavy").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.items >= 1_000
val output = StringBuilder()

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

enum class Outcome { OK, SKIP, FAIL }

class FeedItem(val name: String, val fetch: Long, val transform: Long, val outcome: Outcome, val store: Long) {
    val line: String get() = "FEED $name $fetch $transform ${outcome.name} $store"

    fun withTransform(delay: Long) = FeedItem(name, fetch, delay, outcome, store)
}

class Request(val start: Long, val end: Long)

class Failure(val time: Long, val position: Int)

class Timeline(val requests: List<Request>, val failures: List<Failure>)

val NEVER = Long.MAX_VALUE / 4

// FAIL 을 받은 작업자는 다시 받지 않는다고 보고 요청마다 시작과 응답 시각을 계산한다. 첫 FAIL 시각까지는 실제 실행과 같다.
fun timeline(items: List<FeedItem>, workers: Int, capacity: Int): Timeline {
    val requests = ArrayList<Request>()
    val received = LongArray(items.size)
    val transformed = LongArray(items.size)
    val freeWorkers = PriorityQueue<Long>()
    repeat(minOf(workers, items.size)) { freeWorkers += 0L }
    var collectorTime = 0L
    items.forEachIndexed { index, item ->
        val fetched = collectorTime + item.fetch
        requests += Request(collectorTime, fetched)
        val offered = if (capacity == 0 || index < capacity) fetched else maxOf(fetched, received[index - capacity])
        received[index] = maxOf(offered, freeWorkers.poll())
        transformed[index] = received[index] + item.transform
        freeWorkers += if (item.outcome == Outcome.FAIL) NEVER else transformed[index]
        requests += Request(received[index], transformed[index])
        collectorTime = if (capacity == 0) received[index] else offered
    }
    var storerTime = 0L
    for ((index, item) in items.withIndex()) {
        if (item.outcome == Outcome.FAIL) break
        storerTime = maxOf(storerTime, transformed[index])
        if (item.outcome == Outcome.OK) {
            requests += Request(storerTime, storerTime + item.store)
            storerTime += item.store
        }
    }
    val failures = items.indices
        .filter { items[it].outcome == Outcome.FAIL }
        .map { Failure(transformed[it], it) }
        .sortedBy { it.time }
    return Timeline(requests, failures)
}

// 스펙 5.3: 첫 FAIL 의 응답 시각에는 다른 요청이 시작하거나 응답하지 않아야 한다.
fun firstFailureIsClear(timeline: Timeline): Boolean {
    val failedAt = timeline.failures.firstOrNull()?.time ?: return true
    val touching = timeline.requests.count { it.start == failedAt || it.end == failedAt }
    return touching == 1
}

fun clearFirstFailure(items: MutableList<FeedItem>, workers: Int, capacity: Int) {
    while (true) {
        val timeline = timeline(items, workers, capacity)
        if (firstFailureIsClear(timeline)) return
        val failing = timeline.failures.first().position
        items[failing] = items[failing].withTransform(items[failing].transform + between(1, 3))
    }
}

val NAME_WORDS = listOf("item", "sku", "feed", "a", "b", "doc", "row", "p")

fun distinctNames(count: Int): List<String> {
    val names = LinkedHashSet<String>()
    while (names.size < count) {
        names += if (largeScale || chance(0.2)) {
            "item-${random.nextInt(0, 1_000_000)}"
        } else {
            weighted(
                { "${pick(NAME_WORDS)}${random.nextInt(1, 30)}" } to 7,
                { "${pick(NAME_WORDS)}-${random.nextInt(0, 100)}" } to 2,
                { random.nextInt(0, 50).toString() } to 1,
                { "x".repeat(random.nextInt(18, 21)) } to 1,
            )()
        }
    }
    return names.toList()
}

class Shape(val workers: Int, val capacity: Int)

fun randomShape(size: Int): Shape =
    if (largeScale) {
        Shape(workers = random.nextInt(4, 17), capacity = random.nextInt(0, 17))
    } else {
        Shape(
            workers = weighted(1 to 3, 2 to 4, 3 to 3, 4 to 1, size to 1, size + 2 to 1, 10_000 to 1),
            capacity = weighted(0 to 4, 1 to 3, 2 to 2, 3 to 1, size to 1, size + 3 to 1, 100_000 to 1),
        )
    }

fun randomItems(names: List<String>, shape: Shape): List<FeedItem> {
    val fetchMean = if (largeScale) 10L else weighted(10L to 3, 30L to 1, 3L to 1)
    val transformMean = if (largeScale) shape.workers * between(15, 25) else weighted(30L to 3, 5L to 1, 100L to 1)
    val storeMean = if (largeScale) 8L else weighted(15L to 3, 60L to 1, 2L to 1)
    return names.map { name ->
        FeedItem(
            name = name,
            fetch = between(1, 2 * fetchMean),
            transform = between(1, 2 * transformMean),
            outcome = weighted(Outcome.OK to 8, Outcome.SKIP to 2),
            store = between(1, 2 * storeMean),
        )
    }
}

fun withFailures(items: List<FeedItem>, shape: Shape): List<FeedItem> {
    val failing = items.toMutableList()
    val count = if (largeScale) random.nextInt(1, 4) else weighted(1 to 4, 2 to 1)
    repeat(count) {
        val index = if (largeScale) random.nextInt(items.size * 2 / 5, items.size * 4 / 5) else random.nextInt(items.size)
        val item = failing[index]
        failing[index] = FeedItem(item.name, item.fetch, item.transform, Outcome.FAIL, item.store)
    }
    clearFirstFailure(failing, shape.workers, shape.capacity)
    return failing
}

fun pipelineSize(): Int =
    if (largeScale) {
        options.items + random.nextInt(-options.items / 10, options.items / 10 + 1)
    } else {
        weighted({ options.items } to 3, { random.nextInt(1, 2 * options.items + 1) } to 6, { 1 } to 1)()
    }

fun spaced(line: String): String =
    if (chance(0.03)) "  " + line.split(' ').joinToString("  ") + "   " else line

var traceOn = true
repeat(options.pipelines) { index ->
    val names = distinctNames(pipelineSize())
    val shape = randomShape(names.size)
    val plain = randomItems(names, shape)
    val failing = if (largeScale) index % 2 == 1 else chance(0.4)
    val items = if (failing) withFailures(plain, shape) else plain
    if (!largeScale) {
        val switchTrace = if (traceOn) chance(0.1) else chance(0.4)
        if (switchTrace) traceOn = !traceOn
        if (switchTrace) emit(if (traceOn) "TRACE ON" else "TRACE OFF")
    }
    items.forEach { emit(spaced(it.line)) }
    if (!largeScale && chance(0.03)) emit("")
    emit(spaced("PIPELINE ${shape.workers} ${shape.capacity}"))
}

// 스펙 7.2: 첫 상품의 변환을 가장 늦게 끝내 나머지 결과가 모두 저장 단계에서 차례를 기다리게 한다.
if (options.heavyItems > 0) {
    val heavyCount = options.heavyItems + random.nextInt(-options.heavyItems / 10, options.heavyItems / 10 + 1)
    val names = distinctNames(heavyCount)
    val shape = randomShape(names.size)
    val items = randomItems(names, shape)
    val stalledFirst = items.first().withTransform(between(100_000_000, 1_000_000_000))
    emit("TRACE OFF")
    (listOf(stalledFirst) + items.drop(1)).forEach { emit(it.line) }
    emit("PIPELINE ${shape.workers} ${shape.capacity}")
}

System.out.write(output.toString().encodeToByteArray())
System.out.flush()
