import kotlin.random.Random

class Options(val messages: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--messages" to "300", "--seed" to "1")
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

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

fun randomText(alphabet: String, length: Int): String = String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })

fun randomKeyText(length: Int): String = randomText(KEY_ALPHABET, length).takeUnless { it == "-" } ?: "k"

val KEY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-"
val NAME_TAIL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789._-"
val MAX_TICK = 1_000_000_000L

// FNV-1a 해시가 0x80000000, 0x7FFFFFFF, 0, 0xFFFFFFFF 인 키
val BOUNDARY_HASH_KEYS = listOf("V5FGEr", "-AjCh8", "hmEBhv", "FouBSr", "41BBbM")
val COMMON_KEYS = listOf("a", "b", "k1", "order-7", "user-1", "user-2", "x", "A", "--", "-x", "_", "a:b.c", "0")
val UNKNOWN_COMMANDS = listOf(
    "produce", "Produce", "PRODUCED", "FETCHES", "fetch", "Tick", "TICKS", "OFFSET", "segments", "SEGMENT",
    "CREATE_TOPIC", "create", "PARTITION", "DELETE", "LIST", "HELP", "ERR", "init", "group", "tombstone", "-",
)
val BAD_NAMES = listOf("Orders", "1abc", "_a", "-a", "a*", "é", "a,b", "t".repeat(33), "ORDERS", "a/b", "키")
val BAD_NUMBERS = listOf("01", "-1", "+1", "1.0", "1e3", "0x1F", "00", "", "١", "１", "9223372036854775808")

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

    fun append(recordBytes: Long, now: Long) {
        val active = segments.last()
        if (active.recordCount > 0 && active.sizeBytes + recordBytes > segmentBytes) segments.addLast(SegmentModel(logEndOffset))
        val target = segments.last()
        target.sizeBytes += recordBytes
        target.recordCount++
        target.maxTime = now
        sizeBytes += recordBytes
        logEndOffset++
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
        partitions[partition].append(size, now)
        if (key != null) return
        stickyBytes += size
        if (stickyBytes >= batchSize) {
            stickyPartition = (stickyPartition + 1) % partitions.size
            stickyBytes = 0
        }
    }
}

val topics = ArrayList<TopicModel>()
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
    for (topic in topics) for (partition in topic.partitions) partition.applyRetention(topic.retentionMs, topic.retentionBytes, now)
}

fun produce(topic: TopicModel, key: String?, value: String) {
    emit(spaced(listOf("PRODUCE", topic.name, key ?: "-", value)))
    val size = (key?.length ?: 0) + value.length
    if (size > topic.segmentBytes) return
    topic.produce(key, value, now)
    produced++
}

fun freshTopicName(): String {
    val candidates = listOf("orders", "logs", "events", "metrics", "a", "t.1", "x_y", "my-topic", "z9", "s")
    val unused = candidates.filter { name -> topics.none { it.name == name } }
    if (unused.isNotEmpty() && chance(0.8)) return pick(unused)
    while (true) {
        val name = ('a' + random.nextInt(26)) + randomText(NAME_TAIL_ALPHABET, weighted(0 to 1, random.nextInt(1, 8) to 6, 31 to 1))
        if (topics.none { it.name == name }) return name
    }
}

fun randomTopic(name: String): Pair<TopicModel, List<String>> {
    val settings = ArrayList<String>()
    val partitionCount = weighted(1 to 3, random.nextInt(2, 6) to 6, random.nextInt(6, 13) to 1)
    val segmentBytes = weighted(
        1_073_741_824L to 1,
        random.nextLong(5, 41) to 6,
        random.nextLong(41, 300) to 2,
    )
    val retentionMs: Long? = weighted(
        604_800_000L to 3,
        random.nextLong(0, 30) to 1,
        random.nextLong(30, 800) to 4,
        -1L to 1,
        1_000_000_000_000_000L to 1,
    ).takeIf { it != -1L }
    val retentionBytes: Long? = weighted(
        -1L to 4,
        0L to 1,
        random.nextLong(1, 200) to 4,
        1_000_000_000_000_000L to 1,
    ).takeIf { it != -1L }
    val batchSize = weighted(16_384L to 3, random.nextLong(1, 25) to 5, 1_073_741_824L to 1)
    if (segmentBytes != 1_073_741_824L || chance(0.2)) settings += "segment.bytes=$segmentBytes"
    if (retentionMs != 604_800_000L || chance(0.2)) settings += "retention.ms=${retentionMs ?: -1}"
    if (retentionBytes != null || chance(0.2)) settings += "retention.bytes=${retentionBytes ?: -1}"
    if (batchSize != 16_384L || chance(0.2)) settings += "batch.size=$batchSize"
    return TopicModel(name, segmentBytes, retentionMs, retentionBytes, batchSize, partitionCount) to settings
}

fun createRandomTopic() {
    val (topic, settings) = randomTopic(freshTopicName())
    createTopic(topic, settings)
}

fun randomKey(topic: TopicModel): String? = weighted(
    null to 4,
    pick(COMMON_KEYS) to 5,
    "user-${random.nextInt(40)}" to 4,
    randomKeyText(random.nextInt(1, 6)) to 3,
    pick(BOUNDARY_HASH_KEYS) to 1,
    randomKeyText(64) to (if (topic.segmentBytes >= 64) 1 else 0),
)

fun randomValue(room: Long): String {
    val fitting = room.coerceIn(1, 256).toInt()
    val length = weighted(
        random.nextInt(1, minOf(fitting, 5) + 1) to 14,
        random.nextInt(1, minOf(fitting, 19) + 1) to 4,
        fitting to 2,
        (room + 1).coerceIn(1, 256).toInt() to 1,
        256 to 1,
    )
    return weighted(
        randomText(KEY_ALPHABET, length) to 6,
        "v".padEnd(length, 'x') to 1,
        ("paid:" + randomText("0123456789", length)).take(length) to 1,
    )
}

fun produceRandom() {
    val topic = pick(topics)
    val key = randomKey(topic)
    val room = topic.segmentBytes - (key?.length ?: 0)
    produce(topic, key, randomValue(room))
}

fun offsetArgument(partition: PartitionModel?): String {
    if (partition == null) return random.nextLong(0, 20).toString()
    val start = partition.logStartOffset
    val end = partition.logEndOffset
    return weighted(
        start to 3,
        end to 3,
        end + 1 to 2,
        (start - 1).coerceAtLeast(0) to 2,
        (if (end > start) random.nextLong(start, end + 1) else start) to 6,
        (end - random.nextLong(0, 4)).coerceAtLeast(0) to 3,
        Long.MAX_VALUE to 1,
    ).toString()
}

fun partitionArgument(topic: TopicModel?): Pair<String, PartitionModel?> {
    if (topic == null) return random.nextInt(0, 4).toString() to null
    val index = weighted(
        random.nextInt(topic.partitions.size) to 12,
        topic.partitions.size to 1,
        999 to 1,
    )
    return index.toString() to topic.partitions.getOrNull(index)
}

fun knownOrMissingTopic(): TopicModel? = if (chance(0.04)) null else pick(topics)

fun topicNameOf(topic: TopicModel?): String = topic?.name ?: pick(listOf("nope", "missing", "ghost"))

fun fetch() {
    val topic = knownOrMissingTopic()
    val (partitionText, partition) = partitionArgument(topic)
    val maxRecords = weighted(1 to 4, random.nextInt(2, 6) to 4, random.nextInt(6, 50) to 1, 1_000 to 1)
    emit(spaced(listOf("FETCH", topicNameOf(topic), partitionText, offsetArgument(partition), maxRecords.toString())))
}

fun showOffsets() {
    val topic = knownOrMissingTopic()
    emit(spaced(listOf("OFFSETS", topicNameOf(topic), partitionArgument(topic).first)))
}

fun showSegments() {
    val topic = knownOrMissingTopic()
    emit(spaced(listOf("SEGMENTS", topicNameOf(topic), partitionArgument(topic).first)))
}

fun randomTick() {
    tick(weighted(random.nextLong(1, 11) to 8, random.nextLong(11, 300) to 6, random.nextLong(300, 3_000) to 2, MAX_TICK to 1))
}

fun boundaryTick() {
    val candidates = topics.flatMap { topic -> topic.partitions.filter { it.segments.size > 1 }.map { topic to it } }
    if (candidates.isEmpty()) return randomTick()
    val (topic, partition) = pick(candidates)
    val retentionMs = topic.retentionMs ?: return randomTick()
    val untilBoundary = partition.segments.first().maxTime + retentionMs - now
    if (untilBoundary !in 1..MAX_TICK) return randomTick()
    val index = topic.partitions.indexOf(partition)
    tick(untilBoundary)
    emit("OFFSETS ${topic.name} $index")
    tick(1)
    emit("OFFSETS ${topic.name} $index")
}

fun addPartitions() {
    val topic = knownOrMissingTopic()
    val current = topic?.partitions?.size ?: 1
    val target = weighted(
        (current + random.nextInt(1, 4)).coerceAtMost(1_000) to 6,
        current to 2,
        (current - 1).coerceAtLeast(1) to 1,
    )
    emit(spaced(listOf("PARTITIONS", topicNameOf(topic), target.toString())))
    if (topic != null && target > current) topic.grow(target)
}

fun createDuplicateTopic() {
    val existing = pick(topics)
    emit(spaced(listOf("CREATE", existing.name, random.nextInt(1, 5).toString())))
}

fun malformedLine(): String {
    val topic = pick(topics).name
    return weighted(
        "${pick(UNKNOWN_COMMANDS)} $topic 0" to 3,
        "CREATE ${pick(BAD_NAMES)} 1" to 2,
        "CREATE x${random.nextInt(100)} ${pick(listOf("0", "1001", "01", "-3", "two"))}" to 2,
        "CREATE y${random.nextInt(100)} 1 ${pick(BAD_SETTINGS)}" to 4,
        "CREATE z${random.nextInt(100)}" to 1,
        "PARTITIONS $topic" to 1,
        "PARTITIONS $topic ${pick(listOf("0", "1001", "01"))}" to 1,
        "PRODUCE $topic" to 1,
        "PRODUCE $topic k" to 1,
        "PRODUCE $topic k v extra" to 1,
        "PRODUCE ${pick(BAD_NAMES)} k v" to 1,
        "PRODUCE $topic ${pick(BAD_KEYS)} v" to 2,
        "PRODUCE $topic k ${pick(BAD_VALUES)}" to 2,
        "FETCH $topic 0 0" to 1,
        "FETCH $topic ${pick(BAD_NUMBERS)} 0 1" to 1,
        "FETCH $topic 0 ${pick(BAD_NUMBERS)} 1" to 1,
        "FETCH $topic 0 0 ${pick(listOf("0", "1001", "01"))}" to 1,
        "FETCH $topic 1000 0 1" to 1,
        "OFFSETS $topic" to 1,
        "OFFSETS $topic 0 0" to 1,
        "SEGMENTS $topic ${pick(BAD_NUMBERS)}" to 1,
        "TICK" to 1,
        "TICK ${pick(listOf("0", "1000000001", "-5", "01", "1 2"))}" to 2,
        "TICK\t5" to 1,
        "OFFSETS $topic 0\r" to 1,
        "\r" to 1,
    )
}

val BAD_SETTINGS = listOf(
    "retention.ms=-2", "retention.ms=100 retention.ms=200", "linger.ms=5", "segment.bytes", "segment.bytes=0",
    "segment.bytes=1073741825", "segment.bytes=-1", "batch.size=0", "batch.size=", "retention.bytes=1000000000000001", "retention.ms=1000000000000001",
    "retention.ms=01", "=5", "Segment.bytes=10", "retention.ms==5", "batch.size=1 batch.size=1",
)
val BAD_KEYS = listOf("k".repeat(65), "a b", "é", "a*b", "k!", "a,b", "(null)", "키")
val BAD_VALUES = listOf("v".repeat(257), "b!", "é", "a/b", "(x)", "값", "a=b")

fun runNormal() {
    repeat(random.nextInt(2, 5)) { createRandomTopic() }
    while (produced < options.messages) {
        when (weighted(
            "produce" to 40,
            "fetch" to 16,
            "tick" to 12,
            "boundary" to 1,
            "offsets" to 5,
            "segments" to 4,
            "partitions" to 1,
            "create" to 1,
            "duplicate" to 1,
            "malformed" to 6,
            "blank" to 1,
        )) {
            "produce" -> produceRandom()
            "fetch" -> fetch()
            "tick" -> randomTick()
            "boundary" -> boundaryTick()
            "offsets" -> showOffsets()
            "segments" -> showSegments()
            "partitions" -> addPartitions()
            "create" -> if (topics.size < 5) createRandomTopic() else createDuplicateTopic()
            "duplicate" -> createDuplicateTopic()
            "malformed" -> emit(malformedLine())
            else -> emit(pick(listOf("", "   ", " ")))
        }
    }
    for (topic in topics) for (index in topic.partitions.indices) emit("OFFSETS ${topic.name} $index")
}

fun largeKey(topic: TopicModel): String? = when (topic.name) {
    "clicks" -> if (chance(0.2)) null else "u${(50_000 * random.nextDouble() * random.nextDouble()).toInt()}"
    else -> if (chance(0.5)) null else "acct-${random.nextInt(10_000)}"
}

fun largeValue(): String = "v" + randomText("0123456789abcdef", weighted(random.nextInt(2, 8) to 9, random.nextInt(8, 40) to 1))

fun partitionWeightedByLiveRecords(topic: TopicModel): Int {
    val liveRecords = topic.partitions.map { it.logEndOffset - it.logStartOffset + 1 }
    var target = random.nextLong(liveRecords.sum())
    for ((index, live) in liveRecords.withIndex()) {
        if (target < live) return index
        target -= live
    }
    error("가중치 합이 맞지 않는다")
}

fun largeFetch(topic: TopicModel) {
    val index = partitionWeightedByLiveRecords(topic)
    val partition = topic.partitions[index]
    val start = partition.logStartOffset
    val end = partition.logEndOffset
    val offset = weighted(
        (end - random.nextLong(0, 50)).coerceAtLeast(start) to 70,
        (if (end > start) random.nextLong(start, end) else start) to 20,
        start to 3,
        end to 3,
        end + 1 to 2,
        (start - 1).coerceAtLeast(0) to 2,
    )
    emit("FETCH ${topic.name} $index $offset ${random.nextInt(1, 3)}")
}

fun runLarge() {
    val clicks = TopicModel("clicks", segmentBytes = 12_288, retentionMs = 2_100_000, retentionBytes = null, batchSize = 4_096, 3)
    val audit = TopicModel("audit", segmentBytes = 16_384, retentionMs = null, retentionBytes = 1_200_000, batchSize = 16_384, 1)
    createTopic(clicks, listOf("segment.bytes=12288", "retention.ms=2100000", "batch.size=4096"))
    createTopic(audit, listOf("segment.bytes=16384", "retention.ms=-1", "retention.bytes=1200000"))
    val total = options.messages
    val growClicksAt = total * 6 / 10
    val growAuditAt = total * 7 / 10
    val failedGrowths = setOf(total / 5, total / 2, total * 9 / 10)
    for (index in 0 until total) {
        when (index) {
            growClicksAt -> {
                emit("PARTITIONS clicks 4")
                clicks.grow(4)
            }
            growAuditAt -> {
                emit("PARTITIONS audit 2")
                audit.grow(2)
            }
            in failedGrowths -> emit("PARTITIONS clicks 3")
        }
        val topic = if (chance(0.75)) clicks else audit
        produce(topic, largeKey(topic), largeValue())
        if (chance(0.2)) tick(weighted(random.nextLong(1, 21) to 99, random.nextLong(100, 2_000) to 1))
        if (chance(0.2)) largeFetch(if (chance(0.75)) clicks else audit)
        if (chance(0.05)) {
            val topicForOffsets = pick(topics)
            emit("OFFSETS ${topicForOffsets.name} ${random.nextInt(topicForOffsets.partitions.size)}")
        }
        if (random.nextInt(total) < 200) {
            val topicForSegments = pick(topics)
            emit("SEGMENTS ${topicForSegments.name} ${random.nextInt(topicForSegments.partitions.size)}")
        }
    }
}

if (largeScale) runLarge() else runNormal()
output.flush()
