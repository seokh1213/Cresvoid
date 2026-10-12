import java.util.TreeMap
import java.util.TreeSet
import kotlin.random.Random

class Options(val messages: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--messages" to "200", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    val options = Options(
        messages = values.getValue("--messages").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.messages in 1..1_500_000) { "메시지 수는 1~1,500,000 이다" }
    return options
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.messages >= 100_000
val output = System.out.bufferedWriter()

fun emit(line: String) {
    output.write(line)
    output.write("\n")
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun <T> pickOrNull(items: List<T>): T? = if (items.isEmpty()) null else pick(items)

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

fun randomText(alphabet: String, length: Int): String = String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })

val KEY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-"
val MAX_TICK = 1_000_000_000L

val COMMON_KEYS = listOf("a", "b", "k1", "k2", "k3", "order-7", "user-1", "x", "A", "--", "V5FGEr", "41BBbM")
val CONSUMER_NAMES = listOf("c1", "c2", "c3", "c10", "a", "b.x", "z_9", "c-1", "worker.1", "c20", "m", "n")
val GROUP_NAMES = listOf("g", "after", "before", "billing", "audit.v2", "gq", "gr", "a")
val UNKNOWN_COMMANDS = listOf(
    "join", "Join", "JOINS", "poll", "Poll", "POLLS", "commit", "COMMITS", "leave", "crash", "KILL", "SUBSCRIBE",
    "group", "Group", "GROUPS", "ASSIGN", "assignment", "COMMIT_SYNC", "committed", "HEARTBEAT", "REBALANCE",
    "produce", "init", "tombstone", "dirty",
)
val BAD_NAMES = listOf("G", "1g", "_c", "-c", "c*", "é", "c,d", "c".repeat(33), "C1", "a/b", "키")
val BAD_TOPIC_LISTS = listOf("a,,b", "a,", ",a", ",", "a,a", "A", "a;b", "a b")
val BAD_GROUP_SETTINGS = listOf(
    "session.timeout.ms=0", "session.timeout.ms=1000000001", "max.poll.interval.ms=0", "max.poll.interval.ms=01",
    "auto.offset.reset=none", "auto.offset.reset=Earliest", "auto.offset.reset=", "group.instance.id=x",
    "session.timeout.ms", "=5", "retention.ms=5", "session.timeout.ms=10 session.timeout.ms=10",
    "partition.assignment.strategy=range",
)

fun fnv1a(key: String): Int {
    var hash = 2_166_136_261L
    for (byte in key.encodeToByteArray()) hash = ((hash xor (byte.toLong() and 0xFF)) * 16_777_619L) and 0xFFFF_FFFFL
    return hash.toInt()
}

fun keyedPartition(key: String, partitionCount: Int): Int = (fnv1a(key) and Int.MAX_VALUE) % partitionCount

class SegmentModel(val baseOffset: Long) {
    var sizeBytes = 0L
    var recordCount = 0
    var maxTime = 0L
}

class PartitionModel(private val segmentBytes: Long) {
    val segments = ArrayDeque(listOf(SegmentModel(0)))
    var logStartOffset = 0L
    var logEndOffset = 0L
    var sizeBytes = 0L

    fun append(recordSizes: List<Long>, now: Long) {
        val batchBytes = recordSizes.sum()
        val active = segments.last()
        if (active.recordCount > 0 && active.sizeBytes + batchBytes > segmentBytes) segments.addLast(SegmentModel(logEndOffset))
        val target = segments.last()
        target.sizeBytes += batchBytes
        target.recordCount += recordSizes.size
        target.maxTime = now
        sizeBytes += batchBytes
        logEndOffset += recordSizes.size
    }

    fun applyRetention(retentionMs: Long?, retentionBytes: Long?, now: Long) {
        if (retentionMs != null) deleteOldestWhile { now - it.maxTime > retentionMs }
        if (retentionBytes != null) deleteOldestWhile { sizeBytes - it.sizeBytes >= retentionBytes }
    }

    private fun deleteOldestWhile(condition: (SegmentModel) -> Boolean) {
        while (segments.size > 1 && condition(segments.first())) {
            sizeBytes -= segments.removeFirst().sizeBytes
            logStartOffset = segments.first().baseOffset
        }
    }
}

class TopicModel(
    val name: String,
    val segmentBytes: Long,
    val retentionMs: Long?,
    val retentionBytes: Long?,
    val batchSize: Long,
    partitionCount: Int,
) {
    val partitions = ArrayList<PartitionModel>()
    var stickyPartition = 0
    var stickyBytes = 0L

    init {
        grow(partitionCount)
    }

    fun grow(partitionCount: Int) {
        while (partitions.size < partitionCount) partitions += PartitionModel(segmentBytes)
    }

    fun produce(key: String?, value: String, now: Long) {
        val size = (key?.length ?: 0).toLong() + value.length
        val partition = if (key == null) stickyPartition else keyedPartition(key, partitions.size)
        partitions[partition].append(listOf(size), now)
        if (key != null) return
        stickyBytes += size
        if (stickyBytes >= batchSize) {
            stickyPartition = (stickyPartition + 1) % partitions.size
            stickyBytes = 0
        }
    }
}

enum class ConsumerState { RUNNING, CRASHED, OUT_OF_GROUP }

class GroupModel(val name: String, val topics: List<TopicModel>, val sessionTimeoutMs: Long, val maxPollIntervalMs: Long) {
    var generation = 0
    val consumers = TreeMap<String, ConsumerModel>()

    val members: List<ConsumerModel> get() = consumers.values.filter { it.state != ConsumerState.OUT_OF_GROUP }
}

class ConsumerModel(val name: String, val group: GroupModel) {
    var state = ConsumerState.OUT_OF_GROUP
    var deadline = 0L
    var knownGeneration = 0
}

data class DeadlineEntry(val time: Long, val consumer: String) : Comparable<DeadlineEntry> {
    override fun compareTo(other: DeadlineEntry): Int = compareValuesBy(this, other, DeadlineEntry::time, DeadlineEntry::consumer)
}

val topics = ArrayList<TopicModel>()
val groups = LinkedHashMap<String, GroupModel>()
val consumers = HashMap<String, ConsumerModel>()
val consumerList = ArrayList<ConsumerModel>()
val deadlines = TreeSet<DeadlineEntry>()
val initializedProducers = HashSet<String>()
var batchCounter = 0
var now = 0L
var produced = 0

fun spaced(words: List<String>): String {
    if (largeScale || chance(0.85)) return words.joinToString(" ")
    val gaps = words.joinToString("") { word -> word + " ".repeat(random.nextInt(1, 4)) }.trimEnd(' ')
    return " ".repeat(random.nextInt(0, 3)) + gaps + " ".repeat(random.nextInt(0, 3))
}

fun createTopic(topic: TopicModel, settings: List<String>) {
    emit(spaced(listOf("CREATE", topic.name, topic.partitions.size.toString()) + settings.shuffled(random)))
    topics += topic
}

fun setState(consumer: ConsumerModel, state: ConsumerState, deadline: Long) {
    if (consumer.state != ConsumerState.OUT_OF_GROUP) deadlines.remove(DeadlineEntry(consumer.deadline, consumer.name))
    consumer.state = state
    consumer.deadline = deadline
    if (state != ConsumerState.OUT_OF_GROUP) deadlines.add(DeadlineEntry(deadline, consumer.name))
}

fun forget(consumer: ConsumerModel) {
    setState(consumer, ConsumerState.OUT_OF_GROUP, 0)
    consumers.remove(consumer.name)
    consumer.group.consumers.remove(consumer.name)
}

fun expireMembers() {
    while (deadlines.isNotEmpty() && deadlines.first().time <= now) {
        val consumer = consumers.getValue(deadlines.first().consumer)
        if (consumer.state == ConsumerState.RUNNING) setState(consumer, ConsumerState.OUT_OF_GROUP, 0) else forget(consumer)
        consumer.group.generation++
    }
}

fun tick(millis: Long) {
    emit(spaced(listOf("TICK", millis.toString())))
    now += millis
    expireMembers()
    for (topic in topics) for (partition in topic.partitions) partition.applyRetention(topic.retentionMs, topic.retentionBytes, now)
}

fun produce(topic: TopicModel, key: String?, value: String) {
    emit(spaced(listOf("PRODUCE", topic.name, key ?: "-", value)))
    if ((key?.length ?: 0) + value.length <= topic.segmentBytes) topic.produce(key, value, now)
    produced++
}

fun createGroup(group: GroupModel, strategy: String, settings: List<String>) {
    val topicList = group.topics.map { it.name }.shuffled(random).joinToString(",")
    emit(spaced(listOf("GROUP", group.name, strategy, topicList) + settings.shuffled(random)))
    groups[group.name] = group
}

fun join(group: GroupModel, name: String) {
    emit(spaced(listOf("JOIN", group.name, name)))
    if (name in consumers) return
    val consumer = ConsumerModel(name, group)
    consumers[name] = consumer
    consumerList += consumer
    group.consumers[name] = consumer
    syncMembership(consumer)
}

fun syncMembership(consumer: ConsumerModel) {
    val group = consumer.group
    val rejoining = consumer.state == ConsumerState.OUT_OF_GROUP
    setState(consumer, ConsumerState.RUNNING, now + group.maxPollIntervalMs)
    if (rejoining) group.generation++
    consumer.knownGeneration = group.generation
}

fun poll(consumer: ConsumerModel, maxRecords: Int) {
    emit(spaced(listOf("POLL", consumer.name, maxRecords.toString())))
    if (!isLive(consumer) || consumer.state == ConsumerState.CRASHED) return
    syncMembership(consumer)
}

fun commit(consumer: ConsumerModel) {
    emit(spaced(listOf("COMMIT", consumer.name)))
}

fun leave(consumer: ConsumerModel) {
    emit(spaced(listOf("LEAVE", consumer.name)))
    if (!isLive(consumer) || consumer.state == ConsumerState.CRASHED) return
    val wasMember = consumer.state == ConsumerState.RUNNING
    forget(consumer)
    if (wasMember) consumer.group.generation++
}

fun crash(consumer: ConsumerModel) {
    emit(spaced(listOf("CRASH", consumer.name)))
    if (!isLive(consumer)) return
    when (consumer.state) {
        ConsumerState.RUNNING -> setState(consumer, ConsumerState.CRASHED, now + consumer.group.sessionTimeoutMs)
        ConsumerState.OUT_OF_GROUP -> forget(consumer)
        ConsumerState.CRASHED -> Unit
    }
}

fun addPartitions(topic: TopicModel, partitionCount: Int) {
    emit(spaced(listOf("PARTITIONS", topic.name, partitionCount.toString())))
    if (partitionCount <= topic.partitions.size) return
    topic.grow(partitionCount)
    for (group in groups.values) if (topic in group.topics && group.members.isNotEmpty()) group.generation++
}

fun isLive(consumer: ConsumerModel): Boolean = consumers[consumer.name] === consumer

fun liveConsumers(): List<ConsumerModel> = consumerList.filter(::isLive)

fun runningMembers(): List<ConsumerModel> = liveConsumers().filter { it.state == ConsumerState.RUNNING }

fun randomKey(): String? = weighted(null to 4, pick(COMMON_KEYS) to 5, "user-${random.nextInt(20)}" to 3, randomText(KEY_ALPHABET, 3) to 1)

fun randomValue(): String = weighted("o${random.nextInt(100)}" to 5, randomText(KEY_ALPHABET, random.nextInt(1, 6)) to 3, "paid" to 1)

fun produceRandom() {
    produce(pick(topics), randomKey(), randomValue())
}

fun freshConsumerName(): String {
    val unused = CONSUMER_NAMES.filter { it !in consumers }
    if (unused.isNotEmpty() && chance(0.7)) return pick(unused)
    return "c" + random.nextInt(100, 1_000)
}

fun createRandomTopic(name: String) {
    val partitionCount = weighted(1 to 2, random.nextInt(2, 5) to 5)
    val retained = chance(0.35)
    val segmentBytes = if (retained) random.nextLong(6, 30) else weighted(1_073_741_824L to 1, random.nextLong(10, 80) to 1)
    val retentionBytes = if (retained) random.nextLong(6, 40) else null
    val settings = ArrayList<String>()
    if (segmentBytes != 1_073_741_824L) settings += "segment.bytes=$segmentBytes"
    if (retentionBytes != null) settings += "retention.bytes=$retentionBytes"
    createTopic(TopicModel(name, segmentBytes, 604_800_000, retentionBytes, 16_384, partitionCount), settings)
}

fun createRandomGroup(name: String) {
    val subscribed = topics.shuffled(random).take(random.nextInt(1, minOf(3, topics.size) + 1))
    val sessionTimeoutMs = weighted(45_000L to 1, random.nextLong(20, 600) to 4, random.nextLong(600, 3_000) to 2)
    val maxPollIntervalMs = weighted(300_000L to 1, random.nextLong(30, 800) to 4, random.nextLong(800, 5_000) to 2)
    val settings = ArrayList<String>()
    if (sessionTimeoutMs != 45_000L || chance(0.1)) settings += "session.timeout.ms=$sessionTimeoutMs"
    if (maxPollIntervalMs != 300_000L || chance(0.1)) settings += "max.poll.interval.ms=$maxPollIntervalMs"
    when (weighted("earliest" to 3, "latest" to 1, "default" to 1)) {
        "earliest" -> settings += "auto.offset.reset=earliest"
        "latest" -> settings += "auto.offset.reset=latest"
    }
    createGroup(GroupModel(name, subscribed, sessionTimeoutMs, maxPollIntervalMs), pick(listOf("range", "roundrobin")), settings)
}

fun joinRandom() {
    val group = pickOrNull(groups.values.toList()) ?: return
    join(group, freshConsumerName())
}

fun pollRandom() {
    val consumer = pickOrNull(liveConsumers().filter { it.state != ConsumerState.CRASHED }) ?: return joinRandom()
    poll(consumer, weighted(1 to 3, random.nextInt(2, 6) to 5, 1_000 to 1))
}

fun commitRandom() {
    val members = runningMembers()
    val fresh = members.filter { it.knownGeneration == it.group.generation }
    val consumer = pickOrNull(if (chance(0.8)) fresh else liveConsumers()) ?: return pollRandom()
    commit(consumer)
}

fun deadlineTick() {
    val upcoming = deadlines.firstOrNull() ?: return tick(random.nextLong(1, 100))
    val untilDeadline = upcoming.time - now
    val millis = weighted(
        untilDeadline to 3,
        untilDeadline - 1 to 1,
        untilDeadline + random.nextLong(0, 50) to 2,
        (deadlines.lastOrNull()?.time ?: upcoming.time) - now to 1,
    )
    tick(millis.coerceIn(1, MAX_TICK))
}

fun randomTick() {
    tick(weighted(random.nextLong(1, 20) to 6, random.nextLong(20, 400) to 4, random.nextLong(400, 4_000) to 1))
}

fun deliverySemanticsScenario() {
    val group = pickOrNull(groups.values.filter { it.members.size >= 2 }) ?: return joinRandom()
    val first = pickOrNull(group.members.filter { it.state == ConsumerState.RUNNING }) ?: return pollRandom()
    poll(first, random.nextInt(1, 4))
    if (chance(0.5)) commit(first)
    crash(first)
    tick((first.deadline - now).coerceIn(1, MAX_TICK))
    val next = pickOrNull(group.members.filter { it.state == ConsumerState.RUNNING }) ?: return
    poll(next, random.nextInt(2, 6))
    if (chance(0.5)) commit(next)
}

fun staleCommitScenario() {
    val group = pickOrNull(groups.values.toList()) ?: return
    val member = pickOrNull(group.members.filter { it.state == ConsumerState.RUNNING }) ?: return joinRandom()
    poll(member, random.nextInt(1, 4))
    join(group, freshConsumerName())
    commit(member)
    poll(member, random.nextInt(1, 4))
    commit(member)
}

fun simultaneousDeadlineScenario() {
    val running = runningMembers()
    if (running.size < 2) return joinRandom()
    val pair = running.shuffled(random).take(2)
    pair.forEach { poll(it, 1) }
    tick(pair.maxOf { it.group.maxPollIntervalMs }.coerceIn(1, MAX_TICK))
    pair.forEach { if (chance(0.5)) commit(it) else poll(it, 1) }
}

fun showAssignment() {
    val group = pickOrNull(groups.values.toList())
    emit(spaced(listOf("ASSIGNMENT", group?.name ?: "ghost")))
}

fun showCommitted() {
    val group = pickOrNull(groups.values.toList())
    emit(spaced(listOf("COMMITTED", if (group == null || chance(0.05)) "nope" else group.name)))
}

fun fetchRandom() {
    val topic = pick(topics)
    val index = random.nextInt(topic.partitions.size)
    if (chance(0.3)) return emit(spaced(listOf("OFFSETS", topic.name, index.toString())))
    val partition = topic.partitions[index]
    val offset = weighted(
        partition.logStartOffset to 2,
        partition.logEndOffset to 1,
        (partition.logEndOffset - 1).coerceAtLeast(0) to 2,
    )
    emit(spaced(listOf("FETCH", topic.name, index.toString(), offset.toString(), "3")))
}

fun batchAndDeliver() {
    val producer = pick(listOf("p", "app"))
    if (producer !in initializedProducers || chance(0.1)) {
        emit("INIT $producer")
        initializedProducers += producer
    }
    val topic = pick(topics)
    val partition = random.nextInt(topic.partitions.size)
    val messages = List(random.nextInt(1, 4)) { randomKey() to randomValue() }
    val name = "b${batchCounter++}"
    emit(spaced(listOf("BATCH", producer, name, topic.name, partition.toString()) + messages.flatMap { listOf(it.first ?: "-", it.second) }))
    val sizes = messages.map { (it.first?.length ?: 0).toLong() + it.second.length }
    if (sizes.sum() > topic.segmentBytes) return
    emit("DELIVER $name")
    topic.partitions[partition].append(sizes, now)
    if (chance(0.3)) emit("DELIVER $name")
}

fun errorCommand() {
    val live = liveConsumers()
    val crashed = live.filter { it.state == ConsumerState.CRASHED }
    val outOfGroup = live.filter { it.state == ConsumerState.OUT_OF_GROUP }
    val stale = live.filter { it.state == ConsumerState.RUNNING && it.knownGeneration != it.group.generation }
    val group = pickOrNull(groups.values.toList())
    val existingName = pickOrNull(live)?.name
    val variants = buildList<Pair<() -> Unit, Int>> {
        add({ emit("POLL ghost${random.nextInt(10)} 1") } to 2)
        add({ emit("COMMIT nobody") } to 1)
        add({ emit("LEAVE nobody") } to 1)
        add({ emit("CRASH nobody") } to 1)
        add({ emit("JOIN missing c${random.nextInt(10)}") } to 1)
        add({ emit("ASSIGNMENT missing") } to 1)
        if (group != null) {
            add({ emit(spaced(listOf("GROUP", group.name, "range", topics.first().name))) } to 1)
            add({ emit(spaced(listOf("GROUP", group.name, "range", "nope"))) } to 1)
        }
        add({ emit("GROUP fresh${random.nextInt(100)} roundrobin ${topics.first().name},ghost") } to 1)
        if (existingName != null && group != null) {
            add({ join(group, existingName) } to 2)
            add({ emit("JOIN missing $existingName") } to 1)
        }
        if (crashed.isNotEmpty()) {
            add({ poll(pick(crashed), 1) } to 2)
            add({ commit(pick(crashed)) } to 1)
            add({ leave(pick(crashed)) } to 1)
            add({ crash(pick(crashed)) } to 1)
        }
        if (outOfGroup.isNotEmpty()) add({ commit(pick(outOfGroup)) } to 3)
        if (stale.isNotEmpty()) add({ commit(pick(stale)) } to 3)
    }
    weighted(*variants.toTypedArray())()
}

fun malformedLine(): String {
    val topic = pick(topics).name
    val consumer = pickOrNull(liveConsumers())?.name ?: "c1"
    val group = pickOrNull(groups.keys.toList()) ?: "g"
    return weighted(
        "${pick(UNKNOWN_COMMANDS)} $consumer" to 3,
        "GROUP g${random.nextInt(100)} ${pick(listOf("sticky", "Range", "round-robin", "cooperative-sticky", "RANGE"))} $topic" to 2,
        "GROUP g${random.nextInt(100)} range ${pick(BAD_TOPIC_LISTS)}" to 2,
        "GROUP g${random.nextInt(100)} roundrobin $topic ${pick(BAD_GROUP_SETTINGS)}" to 3,
        "GROUP g${random.nextInt(100)} range" to 1,
        "GROUP ${pick(BAD_NAMES)} range $topic" to 1,
        "JOIN $group" to 1,
        "JOIN $group c1 c2" to 1,
        "JOIN $group ${pick(BAD_NAMES)}" to 1,
        "POLL $consumer" to 1,
        "POLL $consumer ${pick(listOf("0", "1001", "01", "-1", "x"))}" to 2,
        "COMMIT" to 1,
        "COMMIT $consumer extra" to 1,
        "LEAVE ${pick(BAD_NAMES)}" to 1,
        "CRASH" to 1,
        "ASSIGNMENT" to 1,
        "COMMITTED $group extra" to 1,
        "COMMITTED ${pick(BAD_NAMES)}" to 1,
        "POLL\t$consumer 1" to 1,
        "COMMIT $consumer\r" to 1,
        "TICK ${pick(listOf("0", "1000000001", "01"))}" to 1,
    )
}

fun runNormal() {
    val topicNames = listOf("orders", "t", "e", "r", "s", "a.b", "x_1").shuffled(random).take(random.nextInt(2, 5))
    topicNames.forEach(::createRandomTopic)
    repeat(random.nextInt(5, 15)) { produceRandom() }
    GROUP_NAMES.shuffled(random).take(random.nextInt(2, 5)).forEach(::createRandomGroup)
    repeat(random.nextInt(3, 8)) { joinRandom() }
    while (produced < options.messages) {
        when (weighted(
            "produce" to 14,
            "poll" to 16,
            "commit" to 8,
            "tick" to 6,
            "deadline-tick" to 5,
            "join" to 5,
            "leave" to 2,
            "crash" to 3,
            "assignment" to 4,
            "committed" to 4,
            "partitions" to 1,
            "fetch" to 2,
            "batch" to 2,
            "semantics" to 2,
            "stale" to 2,
            "simultaneous" to 1,
            "error" to 6,
            "malformed" to 4,
            "group" to 1,
            "blank" to 1,
        )) {
            "produce" -> produceRandom()
            "poll" -> pollRandom()
            "commit" -> commitRandom()
            "tick" -> randomTick()
            "deadline-tick" -> deadlineTick()
            "join" -> joinRandom()
            "leave" -> pickOrNull(liveConsumers().filter { it.state != ConsumerState.CRASHED })?.let(::leave)
            "crash" -> pickOrNull(liveConsumers().filter { it.state != ConsumerState.CRASHED })?.let(::crash)
            "assignment" -> showAssignment()
            "committed" -> showCommitted()
            "partitions" -> pick(topics).let { addPartitions(it, it.partitions.size + if (chance(0.8)) random.nextInt(1, 3) else 0) }
            "fetch" -> fetchRandom()
            "batch" -> batchAndDeliver()
            "semantics" -> deliverySemanticsScenario()
            "stale" -> staleCommitScenario()
            "simultaneous" -> simultaneousDeadlineScenario()
            "error" -> errorCommand()
            "malformed" -> emit(malformedLine())
            "group" -> GROUP_NAMES.firstOrNull { it !in groups }?.let(::createRandomGroup) ?: showAssignment()
            else -> emit(pick(listOf("", "  ")))
        }
    }
    for (group in groups.keys) {
        emit("ASSIGNMENT $group")
        emit("COMMITTED $group")
    }
}

fun steadyTick(): Long = weighted(
    random.nextLong(1, 11) to 35,
    random.nextLong(11, 1_000) to 45,
    random.nextLong(1_000, 3_000) to 20,
)

fun runLarge() {
    repeat(20) { index ->
        val retained = index % 8 == 0
        val topic = if (retained) {
            TopicModel("t$index", 4_096, 604_800_000, 65_536, 16_384, 8)
        } else {
            TopicModel("t$index", 1_073_741_824, 604_800_000, null, 16_384, 8)
        }
        createTopic(topic, if (retained) listOf("segment.bytes=4096", "retention.bytes=65536") else emptyList())
    }
    repeat(10_000) { index ->
        val subscribed = topics.shuffled(random).take(weighted(1 to 4, 2 to 3, 3 to 2, 4 to 1))
        val sessionTimeoutMs = random.nextLong(10_000, 2_000_000)
        val maxPollIntervalMs = random.nextLong(50_000_000, MAX_TICK + 1)
        val settings = listOf(
            "session.timeout.ms=$sessionTimeoutMs",
            "max.poll.interval.ms=$maxPollIntervalMs",
            "auto.offset.reset=${pick(listOf("earliest", "latest"))}",
        )
        createGroup(GroupModel("g$index", subscribed, sessionTimeoutMs, maxPollIntervalMs), pick(listOf("range", "roundrobin")), settings)
    }
    val groupList = groups.values.toList()
    val joinedNames = ArrayList<String>()
    val total = options.messages
    val consumerCount = total / 5
    val growthAt = List(20) { random.nextInt(total) }.toSet()
    for (index in 0 until total) {
        val topic = pick(topics)
        produce(topic, if (chance(0.3)) null else "k${random.nextInt(5_000)}", "v${random.nextInt(1_000)}")
        if (index < consumerCount) {
            val name = "c$index"
            join(pick(groupList), name)
            joinedNames += name
        }
        if (chance(0.3)) tick(steadyTick())
        if (chance(0.08)) {
            val name = pick(joinedNames)
            val consumer = consumers[name]
            if (consumer == null) {
                emit("POLL $name 1")
            } else {
                poll(consumer, random.nextInt(1, 4))
                if (chance(0.3)) commit(consumer)
            }
        }
        if (chance(0.002)) consumers[pick(joinedNames)]?.let(::commit)
        if (chance(0.004)) consumers[pick(joinedNames)]?.let(::crash)
        if (chance(0.002)) consumers[pick(joinedNames)]?.let(::leave)
        if (chance(0.004)) {
            val name = pick(joinedNames)
            if (name !in consumers) join(pick(groupList), name)
        }
        if (chance(0.002)) emit("ASSIGNMENT ${pick(groupList).name}")
        if (chance(0.002)) emit("COMMITTED ${pick(groupList).name}")
        if (index in growthAt) addPartitions(topic, topic.partitions.size + 1)
    }
    repeat(11) { tick(MAX_TICK) }
}

if (largeScale) runLarge() else runNormal()
output.flush()
