import kotlin.random.Random

class Options(val messages: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--messages" to "250", "--seed" to "1")
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
val DIRTY_RATIOS = listOf("0", "1", "0.5", "0.50", "0.05", "0.3", "0.75", "0.99", "0.0", "0.00", "0.1", "0.9")
val BAD_TOPIC_SETTINGS = listOf(
    "cleanup.policy=compacted", "cleanup.policy=Compact", "cleanup.policy=", "cleanup.policy=compact,delete",
    "cleanup.policy=compact cleanup.policy=delete", "min.cleanable.dirty.ratio=.5", "min.cleanable.dirty.ratio=0.555",
    "min.cleanable.dirty.ratio=1.0", "min.cleanable.dirty.ratio=0.", "min.cleanable.dirty.ratio=00",
    "min.cleanable.dirty.ratio=01", "min.cleanable.dirty.ratio=0.5a", "min.cleanable.dirty.ratio=1.00",
    "min.cleanable.dirty.ratio=０.５", "min.cleanable.dirty.ratio=٠.٥", "min.cleanable.dirty.ratio=-0.5",
    "min.cleanable.dirty.ratio=0,5", "min.cleanable.dirty.ratio=2", "delete.retention.ms=-1", "delete.retention.ms=01",
    "delete.retention.ms=1000000000000001", "delete.retention.ms=", "min.compaction.lag.ms=5", "cleanup.policy",
)
val UNKNOWN_COMMANDS = listOf(
    "tombstone", "Tombstone", "TOMBSTONES", "DELETE", "dirty", "Dirty", "DIRTIES", "COMPACT", "CLEAN", "produce",
    "fetch", "init", "join", "poll", "SEGMENT", "-",
)
val BAD_NAMES = listOf("Users", "1abc", "_a", "-a", "a*", "é", "a,b", "t".repeat(33), "a/b", "키")

fun fnv1a(key: String): Int {
    var hash = 2_166_136_261L
    for (byte in key.encodeToByteArray()) hash = ((hash xor (byte.toLong() and 0xFF)) * 16_777_619L) and 0xFFFF_FFFFL
    return hash.toInt()
}

fun keyedPartition(key: String, partitionCount: Int): Int = (fnv1a(key) and Int.MAX_VALUE) % partitionCount

class Message(val key: String?, val value: String?) {
    val sizeBytes: Long get() = (key?.length ?: 0).toLong() + (value?.length ?: 0)
}

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

    fun append(messages: List<Message>, now: Long) {
        val batchBytes = messages.sumOf { it.sizeBytes }
        val active = segments.last()
        if (active.recordCount > 0 && active.sizeBytes + batchBytes > segmentBytes) segments.addLast(SegmentModel(logEndOffset))
        val target = segments.last()
        target.sizeBytes += batchBytes
        target.recordCount += messages.size
        target.maxTime = now
        sizeBytes += batchBytes
        logEndOffset += messages.size
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
    val compacted: Boolean,
    val segmentBytes: Long,
    val retentionMs: Long?,
    val retentionBytes: Long?,
    val keys: List<String>,
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

    fun produce(message: Message, now: Long) {
        val key = message.key
        val partition = if (key == null) stickyPartition else keyedPartition(key, partitions.size)
        partitions[partition].append(listOf(message), now)
        if (key != null) return
        stickyBytes += message.sizeBytes
        if (stickyBytes >= 16_384) {
            stickyPartition = (stickyPartition + 1) % partitions.size
            stickyBytes = 0
        }
    }

    fun applyRetention(now: Long) {
        if (!compacted) for (partition in partitions) partition.applyRetention(retentionMs, retentionBytes, now)
    }
}

val topics = ArrayList<TopicModel>()
val consumers = ArrayList<String>()
val groups = ArrayList<String>()
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

fun tick(millis: Long) {
    emit(spaced(listOf("TICK", millis.toString())))
    now += millis
    for (topic in topics) topic.applyRetention(now)
}

fun produce(topic: TopicModel, message: Message) {
    emit(spaced(listOf("PRODUCE", topic.name, message.key ?: "-", message.value ?: error("값이 없는 메시지는 TOMBSTONE 으로 쓴다"))))
    produced++
    if (topic.compacted && message.key == null) return
    if (message.sizeBytes <= topic.segmentBytes) topic.produce(message, now)
}

fun tombstone(topic: TopicModel, key: String) {
    emit(spaced(listOf("TOMBSTONE", topic.name, key)))
    produced++
    if (key.length <= topic.segmentBytes) topic.produce(Message(key, null), now)
}

fun createCompactTopic(name: String) {
    val partitionCount = weighted(1 to 4, 2 to 3, 3 to 1)
    val segmentBytes = weighted(random.nextLong(6, 20) to 4, random.nextLong(20, 60) to 3, 1_073_741_824L to 1)
    val keys = List(random.nextInt(2, 9)) { index -> listOf("a", "b", "c", "x", "y", "k$index", "user-$index", "--")[index % 8] }.distinct()
    val settings = arrayListOf("cleanup.policy=compact")
    if (segmentBytes != 1_073_741_824L) settings += "segment.bytes=$segmentBytes"
    if (chance(0.8)) settings += "min.cleanable.dirty.ratio=${pick(DIRTY_RATIOS)}"
    when (weighted("zero" to 2, "small" to 4, "default" to 2, "large" to 1)) {
        "zero" -> settings += "delete.retention.ms=0"
        "small" -> settings += "delete.retention.ms=${random.nextLong(1, 400)}"
        "large" -> settings += "delete.retention.ms=1000000000000000"
    }
    if (chance(0.2)) settings += "retention.ms=${random.nextLong(0, 50)}"
    if (chance(0.2)) settings += "retention.bytes=${random.nextLong(0, 20)}"
    createTopic(TopicModel(name, true, segmentBytes, null, null, keys, partitionCount), settings)
}

fun createDeleteTopic(name: String) {
    val segmentBytes = random.nextLong(8, 40)
    val retentionBytes = random.nextLong(10, 60)
    val settings = arrayListOf("segment.bytes=$segmentBytes", "retention.bytes=$retentionBytes")
    if (chance(0.5)) settings += "cleanup.policy=delete"
    if (chance(0.4)) settings += "min.cleanable.dirty.ratio=${pick(DIRTY_RATIOS)}"
    if (chance(0.4)) settings += "delete.retention.ms=${random.nextLong(0, 100)}"
    createTopic(TopicModel(name, false, segmentBytes, 604_800_000, retentionBytes, listOf("a", "b", "k1", "order-7"), random.nextInt(1, 3)), settings)
}

fun randomValue(): String = weighted(
    randomText("0123456789", random.nextInt(1, 6)) to 5,
    randomText(KEY_ALPHABET, random.nextInt(1, 4)) to 3,
    "v${random.nextInt(100)}" to 2,
)

fun produceRandom() {
    val topic = pick(topics)
    val key = if (chance(0.06)) null else if (chance(0.9)) pick(topic.keys) else randomText(KEY_ALPHABET, 2).takeUnless { it == "-" }
    if (chance(0.15) && key != null) tombstone(topic, key) else produce(topic, Message(key, randomValue()))
}

fun offsetArgument(partition: PartitionModel): String {
    val start = partition.logStartOffset
    val end = partition.logEndOffset
    return weighted(
        start to 4,
        end to 2,
        end + 1 to 1,
        (if (end > start) random.nextLong(start, end + 1) else start) to 8,
    ).toString()
}

fun fetchRandom() {
    val topic = pick(topics)
    val index = if (chance(0.04)) topic.partitions.size else random.nextInt(topic.partitions.size)
    val partition = topic.partitions.getOrNull(index)
    val offset = if (partition == null) "0" else offsetArgument(partition)
    emit(spaced(listOf("FETCH", topic.name, index.toString(), offset, weighted(1 to 3, random.nextInt(2, 10) to 4, 1_000 to 1).toString())))
}

fun showDirty() {
    val topic = if (chance(0.03)) null else pick(topics)
    val index = when {
        topic == null -> 0
        chance(0.05) -> topic.partitions.size
        else -> random.nextInt(topic.partitions.size)
    }
    emit(spaced(listOf("DIRTY", topic?.name ?: "ghost", index.toString())))
}

fun showLogState() {
    val topic = pick(topics)
    emit(spaced(listOf(if (chance(0.7)) "SEGMENTS" else "OFFSETS", topic.name, random.nextInt(topic.partitions.size).toString())))
}

fun randomTick() {
    tick(weighted(random.nextLong(1, 10) to 6, random.nextLong(10, 200) to 4, random.nextLong(200, 2_000) to 1))
}

fun batchAndDeliver() {
    val producer = pick(listOf("p", "app"))
    if (producer !in initializedProducers || chance(0.1)) {
        emit("INIT $producer")
        initializedProducers += producer
    }
    val topic = pick(topics)
    val partition = random.nextInt(topic.partitions.size)
    val messages = List(random.nextInt(1, 4)) { Message(if (chance(0.15)) null else pick(topic.keys), randomValue()) }
    val name = "b${batchCounter++}"
    emit(spaced(listOf("BATCH", producer, name, topic.name, partition.toString()) + messages.flatMap { listOf(it.key ?: "-", it.value ?: "") }))
    if (topic.compacted && messages.any { it.key == null }) return
    if (messages.sumOf { it.sizeBytes } > topic.segmentBytes) return
    emit("DELIVER $name")
    topic.partitions[partition].append(messages, now)
}

fun groupActivity() {
    if (groups.isEmpty() || chance(0.05)) {
        val compacted = topics.filter { it.compacted }.ifEmpty { topics }
        val name = "g${groups.size}"
        val subscribed = compacted.shuffled(random).take(random.nextInt(1, minOf(2, compacted.size) + 1)).joinToString(",") { it.name }
        emit("GROUP $name ${pick(listOf("range", "roundrobin"))} $subscribed auto.offset.reset=earliest max.poll.interval.ms=1000000000 session.timeout.ms=1000000000")
        groups += name
    }
    if (consumers.size < 4 && chance(0.4)) {
        val name = "c${consumers.size + 1}"
        emit("JOIN ${pick(groups)} $name")
        consumers += name
        return
    }
    val consumer = pickOrNull(consumers) ?: return
    when (weighted("poll" to 6, "commit" to 3, "assignment" to 1, "committed" to 1)) {
        "poll" -> emit("POLL $consumer ${random.nextInt(1, 6)}")
        "commit" -> emit("COMMIT $consumer")
        "assignment" -> emit("ASSIGNMENT ${pick(groups)}")
        else -> emit("COMMITTED ${pick(groups)}")
    }
}

fun failingWrite() {
    val compacted = pickOrNull(topics.filter { it.compacted })
    val deleted = pickOrNull(topics.filter { !it.compacted })
    val variants = buildList<Pair<() -> Unit, Int>> {
        add({ emit("TOMBSTONE ghost k") } to 1)
        add({ emit("PRODUCE ghost - v") } to 1)
        add({ emit("DIRTY ghost 0") } to 1)
        if (compacted != null) {
            add({ produced++; emit(spaced(listOf("PRODUCE", compacted.name, "-", randomValue()))) } to 3)
            add({ produced++; emit("PRODUCE ${compacted.name} - ${"v".repeat(256)}") } to 1)
            add({ emit("BATCH p f${batchCounter++} ${compacted.name} 0 k1 v1 - v2") } to 2)
            add({ emit("BATCH p f${batchCounter++} ${compacted.name} ${compacted.partitions.size} - v2") } to 1)
            add({ emit("BATCH p f${batchCounter++} ${compacted.name} 0 - ${"v".repeat(256)} - ${"w".repeat(256)}") } to 1)
            add({ emit("DIRTY ${compacted.name} ${compacted.partitions.size}") } to 1)
            if (compacted.segmentBytes < 64) add({ tombstone(compacted, "k".repeat(compacted.segmentBytes.toInt() + 1)) } to 1)
        }
        if (deleted != null) {
            add({ emit("DIRTY ${deleted.name} 0") } to 2)
            add({ emit("DIRTY ${deleted.name} ${deleted.partitions.size}") } to 1)
        }
    }
    if ("p" !in initializedProducers) {
        emit("INIT p")
        initializedProducers += "p"
    }
    weighted(*variants.toTypedArray())()
}

fun malformedLine(): String {
    val topic = pick(topics).name
    return weighted(
        "${pick(UNKNOWN_COMMANDS)} $topic 0" to 3,
        "CREATE n${random.nextInt(100)} 1 ${pick(BAD_TOPIC_SETTINGS)}" to 6,
        "TOMBSTONE $topic -" to 2,
        "TOMBSTONE $topic" to 1,
        "TOMBSTONE $topic k extra" to 1,
        "TOMBSTONE ${pick(BAD_NAMES)} k" to 1,
        "TOMBSTONE $topic ${pick(listOf("k".repeat(65), "é", "a*b", "(null)"))}" to 1,
        "DIRTY $topic" to 1,
        "DIRTY $topic 0 0" to 1,
        "DIRTY $topic ${pick(listOf("1000", "01", "-1", "x"))}" to 1,
        "DIRTY ${pick(BAD_NAMES)} 0" to 1,
        "TOMBSTONE\t$topic k" to 1,
        "DIRTY $topic 0\r" to 1,
    )
}

fun runNormal() {
    val names = listOf("users", "acct", "k", "c", "cfg", "s.t", "z_1").shuffled(random)
    val compactCount = random.nextInt(2, 4)
    names.take(compactCount).forEach(::createCompactTopic)
    names.drop(compactCount).take(random.nextInt(1, 3)).forEach(::createDeleteTopic)
    while (produced < options.messages) {
        when (weighted(
            "produce" to 40,
            "tick" to 14,
            "dirty" to 9,
            "fetch" to 9,
            "log-state" to 6,
            "group" to 6,
            "batch" to 3,
            "failing" to 4,
            "malformed" to 4,
            "partitions" to 1,
            "blank" to 1,
        )) {
            "produce" -> produceRandom()
            "tick" -> randomTick()
            "dirty" -> showDirty()
            "fetch" -> fetchRandom()
            "log-state" -> showLogState()
            "group" -> groupActivity()
            "batch" -> batchAndDeliver()
            "failing" -> failingWrite()
            "malformed" -> emit(malformedLine())
            "partitions" -> pick(topics).let { topic ->
                val target = topic.partitions.size + 1
                emit("PARTITIONS ${topic.name} $target")
                topic.grow(target)
            }
            else -> emit(pick(listOf("", "   ")))
        }
    }
    randomTick()
    for (topic in topics) for (index in topic.partitions.indices) {
        emit("SEGMENTS ${topic.name} $index")
        if (topic.compacted) emit("DIRTY ${topic.name} $index")
        emit("FETCH ${topic.name} $index ${topic.partitions[index].logStartOffset} 1000")
    }
}

class LargeTopic(val model: TopicModel, val keySpace: Int)

fun largeFetch(topic: TopicModel) {
    val index = random.nextInt(topic.partitions.size)
    val end = topic.partitions[index].logEndOffset
    val offset = weighted(
        (end - random.nextLong(0, 30)).coerceAtLeast(0) to 82,
        (if (end > 0) random.nextLong(0, end) else 0) to 10,
        0L to 3,
        end to 3,
        end + 1 to 2,
    )
    emit("FETCH ${topic.name} $index $offset ${random.nextInt(1, 3)}")
}

fun runLarge() {
    val compactTopics = listOf(
        LargeTopic(TopicModel("users", true, 8_192, null, null, emptyList(), 2), 160_000) to
            listOf("cleanup.policy=compact", "segment.bytes=8192", "min.cleanable.dirty.ratio=0.5", "delete.retention.ms=200000"),
        LargeTopic(TopicModel("accounts", true, 16_384, null, null, emptyList(), 2), 160_000) to
            listOf("cleanup.policy=compact", "segment.bytes=16384", "min.cleanable.dirty.ratio=0.7"),
        LargeTopic(TopicModel("configs", true, 16_384, null, null, emptyList(), 2), 120_000) to
            listOf("cleanup.policy=compact", "segment.bytes=16384", "min.cleanable.dirty.ratio=0.70", "delete.retention.ms=0"),
        LargeTopic(TopicModel("sessions", true, 16_384, null, null, emptyList(), 2), 160_000) to
            listOf("cleanup.policy=compact", "segment.bytes=16384", "min.cleanable.dirty.ratio=0.5", "delete.retention.ms=1000000"),
    )
    compactTopics.forEach { (topic, settings) -> createTopic(topic.model, settings) }
    val audit = TopicModel("audit", false, 4_096, 604_800_000, 40_000, emptyList(), 1)
    createTopic(audit, listOf("segment.bytes=4096", "retention.bytes=40000"))
    emit("GROUP readers roundrobin users,configs auto.offset.reset=earliest")
    listOf("r1", "r2", "r3").forEach { emit("JOIN readers $it") }
    emit("INIT loader")
    val large = compactTopics.map { it.first }
    var batch = 0
    while (produced < options.messages) {
        val target = pick(large)
        val key = "u${random.nextInt(target.keySpace)}"
        if (chance(0.1)) tombstone(target.model, key) else produce(target.model, Message(key, "v${random.nextInt(1_000)}"))
        if (chance(0.2)) tick(random.nextLong(1, 50))
        if (chance(0.8 * produced / options.messages)) largeFetch(pick(large).model)
        if (chance(0.05)) {
            val dirtyTarget = pick(large).model
            emit("DIRTY ${dirtyTarget.name} ${random.nextInt(dirtyTarget.partitions.size)}")
        }
        if (random.nextInt(options.messages) < 500) {
            val segmentsTarget = pick(large).model
            emit("SEGMENTS ${segmentsTarget.name} ${random.nextInt(segmentsTarget.partitions.size)}")
        }
        if (chance(0.002)) produce(audit, Message(if (chance(0.5)) null else "a${random.nextInt(50)}", "e${random.nextInt(100)}"))
        if (chance(0.001)) {
            val partition = random.nextInt(2)
            val messages = List(random.nextInt(1, 4)) { Message("u${random.nextInt(target.keySpace)}", "b${random.nextInt(100)}") }
            val name = "lb${batch++}"
            emit("BATCH loader $name ${target.model.name} $partition " + messages.joinToString(" ") { "${it.key} ${it.value}" })
            emit("DELIVER $name")
            target.model.partitions[partition].append(messages, now)
        }
        if (chance(0.002)) emit("POLL ${pick(listOf("r1", "r2", "r3"))} ${random.nextInt(1, 4)}")
        if (chance(0.0005)) emit("COMMIT ${pick(listOf("r1", "r2", "r3"))}")
    }
}

if (largeScale) runLarge() else runNormal()
output.flush()
