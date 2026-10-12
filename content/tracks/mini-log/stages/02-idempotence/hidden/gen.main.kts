import kotlin.random.Random

class Options(val batches: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--batches" to "150", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    val options = Options(
        batches = values.getValue("--batches").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
    require(options.batches in 1..900_000) { "배치 수는 1~900,000 이다" }
    return options
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.batches >= 100_000
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

fun randomKeyText(length: Int): String = randomText(KEY_ALPHABET, length).takeUnless { it == "-" } ?: "k"

val KEY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-"
val NAME_TAIL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789._-"
val MAX_TICK = 1_000_000_000L
val CACHED_BATCH_LIMIT = 5

// FNV-1a 해시가 0x80000000, 0x7FFFFFFF, 0, 0xFFFFFFFF 인 키
val BOUNDARY_HASH_KEYS = listOf("V5FGEr", "-AjCh8", "hmEBhv", "FouBSr", "41BBbM")
val COMMON_KEYS = listOf("a", "b", "k1", "order-7", "user-1", "user-2", "x", "A", "--", "-x", "_", "a:b.c", "0")
val UNKNOWN_COMMANDS = listOf(
    "produce", "PRODUCED", "fetch", "Tick", "OFFSET", "segments", "CREATE_TOPIC", "PARTITION", "DELETE", "HELP",
    "init", "Init", "INITS", "batch", "Batch", "BATCHES", "deliver", "DELIVERY", "RESEND", "producers", "PRODUCER",
    "group", "join", "tombstone", "-",
)
val BAD_NAMES = listOf("Orders", "1abc", "_a", "-a", "a*", "é", "a,b", "t".repeat(33), "P1", "a/b", "키")
val BAD_NUMBERS = listOf("01", "-1", "+1", "1.0", "1e3", "0x1F", "00", "١", "１", "9223372036854775808")
val BAD_SETTINGS = listOf(
    "retention.ms=-2", "linger.ms=5", "segment.bytes", "segment.bytes=0", "segment.bytes=-1", "batch.size=",
    "retention.ms=01", "=5", "enable.idempotence=true", "acks=all", "batch.size=1 batch.size=1",
)
val BAD_KEYS = listOf("k".repeat(65), "é", "a*b", "k!", "a,b", "(null)", "키")
val BAD_VALUES = listOf("v".repeat(257), "b!", "é", "a/b", "(x)", "값", "a=b")
val PRODUCER_NAMES = listOf("p", "app", "w", "pay-svc", "p1", "p2", "billing.v2", "x_9", "p10")

fun fnv1a(key: String): Int {
    var hash = 2_166_136_261L
    for (byte in key.encodeToByteArray()) hash = ((hash xor (byte.toLong() and 0xFF)) * 16_777_619L) and 0xFFFF_FFFFL
    return hash.toInt()
}

fun keyedPartition(key: String, partitionCount: Int): Int = (fnv1a(key) and Int.MAX_VALUE) % partitionCount

class Message(val key: String?, val value: String) {
    val sizeBytes: Long get() = (key?.length ?: 0).toLong() + value.length
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

    fun produce(message: Message, now: Long) {
        val key = message.key
        val partition = if (key == null) stickyPartition else keyedPartition(key, partitions.size)
        partitions[partition].append(listOf(message), now)
        if (key != null) return
        stickyBytes += message.sizeBytes
        if (stickyBytes >= batchSize) {
            stickyPartition = (stickyPartition + 1) % partitions.size
            stickyBytes = 0
        }
    }
}

class StreamModel(val producer: String, val producerId: Long, val topic: TopicModel, val partition: Int) {
    val created = ArrayList<BatchModel>()
    var appendedCount = 0
    val cached = ArrayDeque<BatchModel>()

    val nextSequence: Int get() = created.lastOrNull()?.let { it.lastSequence + 1 } ?: 0
    val nextPending: BatchModel? get() = created.getOrNull(appendedCount)
    val pendingCount: Int get() = created.size - appendedCount
}

class BatchModel(val name: String, val stream: StreamModel, val firstSequence: Int, val messages: List<Message>) {
    val lastSequence: Int get() = firstSequence + messages.size - 1
}

class ProducerModel(val name: String, var producerId: Long) {
    val streams = HashMap<Pair<String, Int>, StreamModel>()
}

val topics = ArrayList<TopicModel>()
val producers = LinkedHashMap<String, ProducerModel>()
val streams = ArrayList<StreamModel>()
val batches = HashMap<String, BatchModel>()
val batchList = ArrayList<BatchModel>()
var lastProducerId = 0L
var batchNameCounter = 0
var now = 0L

fun spaced(words: List<String>): String {
    if (largeScale || chance(0.85)) return words.joinToString(" ")
    val gaps = words.joinToString("") { word -> word + " ".repeat(random.nextInt(1, 4)) }.trimEnd(' ')
    return " ".repeat(random.nextInt(0, 3)) + gaps + " ".repeat(random.nextInt(0, 3))
}

fun isOrphan(stream: StreamModel): Boolean = producers.getValue(stream.producer).producerId != stream.producerId

fun createTopic(topic: TopicModel, settings: List<String>) {
    emit(spaced(listOf("CREATE", topic.name, topic.partitions.size.toString()) + settings.shuffled(random)))
    topics += topic
}

fun tick(millis: Long) {
    emit(spaced(listOf("TICK", millis.toString())))
    now += millis
    for (topic in topics) for (partition in topic.partitions) partition.applyRetention(topic.retentionMs, topic.retentionBytes, now)
}

fun produce(topic: TopicModel, message: Message) {
    emit(spaced(listOf("PRODUCE", topic.name, message.key ?: "-", message.value)))
    if (message.sizeBytes <= topic.segmentBytes) topic.produce(message, now)
}

fun initProducer(name: String) {
    emit(spaced(listOf("INIT", name)))
    val producerId = ++lastProducerId
    val existing = producers[name]
    if (existing == null) {
        producers[name] = ProducerModel(name, producerId)
    } else {
        existing.producerId = producerId
        existing.streams.clear()
    }
}

fun nextBatchName(): String = if (largeScale) "b" + (batchNameCounter++).toString(36) else weighted(
    "b${batchNameCounter++}" to 8,
    "m${batchNameCounter++}" to 2,
    "order-batch.${batchNameCounter++}" to 1,
    "x" + randomText(NAME_TAIL_ALPHABET, 31).also { batchNameCounter++ } to 1,
)

fun batchLine(producer: String, batch: String, topic: String, partition: String, messages: List<Message>): String =
    spaced(listOf("BATCH", producer, batch, topic, partition) + messages.flatMap { listOf(it.key ?: "-", it.value) })

fun createBatch(producer: ProducerModel, topic: TopicModel, partition: Int, messages: List<Message>) {
    val name = nextBatchName()
    emit(batchLine(producer.name, name, topic.name, partition.toString(), messages))
    if (messages.sumOf { it.sizeBytes } > topic.segmentBytes) return
    val stream = producer.streams.getOrPut(topic.name to partition) {
        StreamModel(producer.name, producer.producerId, topic, partition).also { streams += it }
    }
    val batch = BatchModel(name, stream, stream.nextSequence, messages)
    stream.created += batch
    batches[name] = batch
    batchList += batch
}

fun deliver(batch: BatchModel) {
    emit(spaced(listOf("DELIVER", batch.name)))
    val stream = batch.stream
    if (batch in stream.cached || stream.nextPending !== batch) return
    stream.topic.partitions[stream.partition].append(batch.messages, now)
    stream.appendedCount++
    stream.cached.addLast(batch)
    if (stream.cached.size > CACHED_BATCH_LIMIT) stream.cached.removeFirst()
}

fun freshTopicName(): String {
    val candidates = listOf("pay", "orders", "logs", "q", "r", "s", "t.1", "x_y", "my-topic", "z9")
    val unused = candidates.filter { name -> topics.none { it.name == name } }
    if (unused.isNotEmpty() && chance(0.85)) return pick(unused)
    while (true) {
        val name = ('a' + random.nextInt(26)) + randomText(NAME_TAIL_ALPHABET, random.nextInt(1, 8))
        if (topics.none { it.name == name }) return name
    }
}

fun createRandomTopic() {
    val partitionCount = weighted(1 to 3, random.nextInt(2, 5) to 6, random.nextInt(5, 9) to 1)
    val segmentBytes = weighted(1_073_741_824L to 2, random.nextLong(12, 60) to 5, random.nextLong(60, 400) to 3)
    val retentionMs: Long? = weighted(604_800_000L to 4, random.nextLong(30, 800) to 3, -1L to 1).takeIf { it != -1L }
    val retentionBytes: Long? = weighted(-1L to 5, random.nextLong(10, 200) to 3).takeIf { it != -1L }
    val batchSize = weighted(16_384L to 3, random.nextLong(1, 25) to 4)
    val settings = ArrayList<String>()
    if (segmentBytes != 1_073_741_824L) settings += "segment.bytes=$segmentBytes"
    if (retentionMs != 604_800_000L) settings += "retention.ms=${retentionMs ?: -1}"
    if (retentionBytes != null) settings += "retention.bytes=$retentionBytes"
    if (batchSize != 16_384L) settings += "batch.size=$batchSize"
    createTopic(TopicModel(freshTopicName(), segmentBytes, retentionMs, retentionBytes, batchSize, partitionCount), settings)
}

fun randomKey(): String? = weighted(
    null to 5,
    pick(COMMON_KEYS) to 5,
    "user-${random.nextInt(30)}" to 3,
    randomKeyText(random.nextInt(1, 5)) to 2,
    pick(BOUNDARY_HASH_KEYS) to 1,
)

fun randomValue(): String = weighted(
    randomText(KEY_ALPHABET, random.nextInt(1, 5)) to 6,
    "paid" to 1,
    "v${random.nextInt(100)}" to 2,
    randomText(KEY_ALPHABET, random.nextInt(5, 13)) to 1,
)

fun randomMessage(): Message = Message(randomKey(), randomValue())

fun padToSize(messages: List<Message>, targetBytes: Long): List<Message> {
    val last = messages.last()
    val room = targetBytes - (messages.sumOf { it.sizeBytes } - last.value.length)
    if (room !in 1..256) return messages
    return messages.dropLast(1) + Message(last.key, "v".padEnd(room.toInt(), '0'))
}

fun batchMessages(topic: TopicModel): List<Message> {
    val count = weighted(1 to 6, 2 to 5, random.nextInt(3, 5) to 3, random.nextInt(5, 11) to 1, 100 to 1)
    if (count >= 50) return List(count) { Message(null, randomText("abc", random.nextInt(1, 3))) }
    val messages = List(count) { randomMessage() }
    return when (weighted("plain" to 10, "exact" to 2, "over" to 1)) {
        "exact" -> padToSize(messages, topic.segmentBytes)
        "over" -> padToSize(messages, topic.segmentBytes + 1)
        else -> messages
    }
}

fun createRandomBatch() {
    val producer = pickOrNull(producers.values.toList()) ?: return initProducer(pick(PRODUCER_NAMES))
    val topic = pick(topics)
    createBatch(producer, topic, random.nextInt(topic.partitions.size), batchMessages(topic))
}

fun oversizedMessages(segmentBytes: Long): List<Message> {
    val total = segmentBytes + 1
    return (0 until total step 256).map { start -> Message(null, "v".repeat(minOf(256L, total - start).toInt())) }
}

fun failingBatch() {
    val topic = pick(topics)
    val knownProducer = pickOrNull(producers.values.toList())?.name ?: return initProducer(pick(PRODUCER_NAMES))
    val unknownProducer = PRODUCER_NAMES.firstOrNull { it !in producers } ?: "nobody"
    val existingBatch = pickOrNull(batchList)?.name
    val smallTopic = pickOrNull(topics.filter { it.segmentBytes < 700 })
    val freshBatch = "f${batchNameCounter++}"
    val messages = listOf(randomMessage())
    val variants = buildList<Pair<() -> String, Int>> {
        add({ batchLine(unknownProducer, freshBatch, topic.name, "0", messages) } to 3)
        add({ batchLine(knownProducer, freshBatch, "ghost", "0", messages) } to 2)
        add({ batchLine(knownProducer, freshBatch, topic.name, topic.partitions.size.toString(), messages) } to 2)
        add({ batchLine(knownProducer, freshBatch, topic.name, "999", oversizedMessages(400)) } to 1)
        if (existingBatch != null) {
            add({ batchLine(unknownProducer, existingBatch, "nope", "0", messages) } to 1)
            add({ batchLine(knownProducer, existingBatch, topic.name, "0", messages) } to 3)
            add({ batchLine(knownProducer, existingBatch, "nope", "0", messages) } to 1)
        }
        if (smallTopic != null) {
            add({ batchLine(knownProducer, freshBatch, smallTopic.name, "0", oversizedMessages(smallTopic.segmentBytes)) } to 2)
        }
    }
    emit(weighted(*variants.toTypedArray())())
}

fun deliverRandom() {
    val choice = weighted(
        "next" to 40,
        "next-twice" to 6,
        "duplicate" to 12,
        "early" to 10,
        "evicted" to 6,
        "late" to 8,
        "any" to 10,
        "unknown" to 4,
    )
    val pending = streams.filter { it.pendingCount > 0 }
    val batch = when (choice) {
        "next", "next-twice" -> pickOrNull(pending.filterNot(::isOrphan))?.nextPending
        "duplicate" -> pickOrNull(streams.filter { it.cached.isNotEmpty() })?.cached?.let(::pick)
        "early" -> pickOrNull(pending.filter { it.pendingCount > 1 })?.let { it.created[it.appendedCount + random.nextInt(1, it.pendingCount)] }
        "evicted" -> pickOrNull(streams.filter { it.appendedCount > CACHED_BATCH_LIMIT })?.let {
            it.created[random.nextInt(it.appendedCount - CACHED_BATCH_LIMIT)]
        }
        "late" -> pickOrNull(pending.filter(::isOrphan))?.nextPending
        "any" -> pickOrNull(batchList)
        else -> null
    }
    if (choice == "unknown" || batchList.isEmpty()) {
        emit(spaced(listOf("DELIVER", pick(listOf("zz", "nope", "f${random.nextInt(batchNameCounter + 1)}", "B1")))))
        return
    }
    val chosen = batch ?: pick(batchList)
    deliver(chosen)
    if (choice == "next-twice") deliver(chosen)
}

fun showProducers() {
    val topic = if (chance(0.04)) null else pick(topics)
    val partition = when {
        topic == null -> random.nextInt(3)
        chance(0.08) -> topic.partitions.size
        else -> random.nextInt(topic.partitions.size)
    }
    emit(spaced(listOf("PRODUCERS", topic?.name ?: "ghost", partition.toString())))
}

fun offsetArgument(partition: PartitionModel?): String {
    if (partition == null) return random.nextLong(0, 20).toString()
    val start = partition.logStartOffset
    val end = partition.logEndOffset
    return weighted(
        start to 3,
        end to 3,
        end + 1 to 2,
        (start - 1).coerceAtLeast(0) to 1,
        (if (end > start) random.nextLong(start, end + 1) else start) to 6,
    ).toString()
}

fun fetch() {
    val topic = pick(topics)
    val index = if (chance(0.05)) topic.partitions.size else random.nextInt(topic.partitions.size)
    val maxRecords = weighted(1 to 3, random.nextInt(2, 6) to 4, random.nextInt(6, 30) to 2, 1_000 to 1)
    emit(spaced(listOf("FETCH", topic.name, index.toString(), offsetArgument(topic.partitions.getOrNull(index)), maxRecords.toString())))
}

fun showLogState() {
    val topic = pick(topics)
    val command = if (chance(0.6)) "OFFSETS" else "SEGMENTS"
    emit(spaced(listOf(command, topic.name, random.nextInt(topic.partitions.size).toString())))
}

fun randomTick() {
    tick(weighted(random.nextLong(1, 11) to 8, random.nextLong(11, 300) to 6, random.nextLong(300, 3_000) to 2, MAX_TICK to 1))
}

fun addPartitions() {
    val topic = pick(topics)
    val current = topic.partitions.size
    val target = if (chance(0.75)) current + random.nextInt(1, 3) else current
    emit(spaced(listOf("PARTITIONS", topic.name, target.toString())))
    if (target > current) topic.grow(target)
}

fun malformedLine(): String {
    val topic = pick(topics).name
    val producer = pickOrNull(producers.keys.toList()) ?: "p"
    val batch = pickOrNull(batchList)?.name ?: "b0"
    val oversizedBatch = List(101) { "- a" }.joinToString(" ")
    return weighted(
        "${pick(UNKNOWN_COMMANDS)} $producer" to 3,
        "INIT" to 1,
        "INIT $producer extra" to 1,
        "INIT ${pick(BAD_NAMES)}" to 2,
        "BATCH $producer f${batchNameCounter++} $topic 0 - " to 2,
        "BATCH $producer f${batchNameCounter++} $topic 0" to 1,
        "BATCH $producer f${batchNameCounter++} $topic 0 k" to 1,
        "BATCH $producer f${batchNameCounter++} $topic 0 $oversizedBatch" to 1,
        "BATCH $producer f${batchNameCounter++} $topic ${pick(BAD_NUMBERS + "1000")} - v" to 2,
        "BATCH $producer ${pick(BAD_NAMES)} $topic 0 - v" to 1,
        "BATCH ${pick(BAD_NAMES)} f${batchNameCounter++} $topic 0 - v" to 1,
        "BATCH $producer f${batchNameCounter++} ${pick(BAD_NAMES)} 0 - v" to 1,
        "BATCH $producer f${batchNameCounter++} $topic 0 ${pick(BAD_KEYS)} v" to 1,
        "BATCH $producer f${batchNameCounter++} $topic 0 k ${pick(BAD_VALUES)}" to 1,
        "DELIVER" to 1,
        "DELIVER $batch $batch" to 1,
        "DELIVER ${pick(BAD_NAMES)}" to 1,
        "PRODUCERS $topic" to 1,
        "PRODUCERS $topic 0 0" to 1,
        "PRODUCERS $topic ${pick(BAD_NUMBERS + "1000")}" to 1,
        "CREATE y${random.nextInt(100)} 1 ${pick(BAD_SETTINGS)}" to 1,
        "PRODUCE $topic ${pick(BAD_KEYS)} v" to 1,
        "FETCH $topic 0 ${pick(BAD_NUMBERS)} 1" to 1,
        "TICK ${pick(listOf("0", "1000000001", "-5", "01"))}" to 1,
        "DELIVER\t$batch" to 1,
        "DELIVER $batch\r" to 1,
    )
}

fun runNormal() {
    repeat(random.nextInt(2, 4)) { createRandomTopic() }
    repeat(random.nextInt(1, 4)) { initProducer(PRODUCER_NAMES[it]) }
    while (batchList.size < options.batches) {
        when (weighted(
            "batch" to 24,
            "failing-batch" to 3,
            "deliver" to 34,
            "init" to 1,
            "producers" to 5,
            "produce" to 8,
            "fetch" to 6,
            "tick" to 5,
            "log-state" to 4,
            "partitions" to 1,
            "malformed" to 5,
            "blank" to 1,
        )) {
            "batch" -> createRandomBatch()
            "failing-batch" -> failingBatch()
            "deliver" -> deliverRandom()
            "init" -> initProducer(pick(PRODUCER_NAMES.take(producers.size + 1)))
            "producers" -> showProducers()
            "produce" -> produce(pick(topics), randomMessage())
            "fetch" -> fetch()
            "tick" -> randomTick()
            "log-state" -> showLogState()
            "partitions" -> addPartitions()
            "malformed" -> emit(malformedLine())
            else -> emit(pick(listOf("", "   ")))
        }
    }
    for (topic in topics) for (index in topic.partitions.indices) emit("PRODUCERS ${topic.name} $index")
}

fun largeMessages(): List<Message> {
    val count = weighted(1 to 30, 2 to 25, 3 to 20, 4 to 15, random.nextInt(5, 9) to 10)
    return List(count) {
        val key = if (chance(0.5)) null else "k${random.nextInt(if (chance(0.9)) 100 else 5_000)}"
        Message(key, "v" + randomText("0123456789", random.nextInt(0, 3)))
    }
}

fun deliverLarge() {
    val active = streams.filter { it.pendingCount > 0 && !isOrphan(it) }
    val choice = weighted("next" to 68, "duplicate" to 19, "early" to 6, "evicted" to 3, "late" to 2, "unknown" to 2)
    if (choice == "unknown") return emit("DELIVER zz${random.nextInt(100)}")
    val batch = when (choice) {
        "next" -> pickOrNull(active)?.nextPending
        "duplicate" -> pickOrNull(streams.filter { it.cached.isNotEmpty() && !isOrphan(it) })?.cached?.let(::pick)
        "early" -> pickOrNull(active.filter { it.pendingCount > 1 })?.let { it.created[it.appendedCount + random.nextInt(1, it.pendingCount)] }
        "evicted" -> pickOrNull(active.filter { it.appendedCount > CACHED_BATCH_LIMIT })?.let {
            it.created[random.nextInt(it.appendedCount - CACHED_BATCH_LIMIT)]
        }
        else -> pickOrNull(streams.filter { it.pendingCount > 1 && isOrphan(it) })?.nextPending
    }
    val chosen = batch
        ?: pickOrNull(active)?.nextPending
        ?: pickOrNull(streams.filter { it.cached.isNotEmpty() })?.cached?.let(::pick)
    if (chosen == null) emit("DELIVER zz${random.nextInt(100)}") else deliver(chosen)
}

fun runLarge() {
    val pay = TopicModel("pay", segmentBytes = 65_536, retentionMs = null, retentionBytes = 4_000_000, batchSize = 16_384, 2)
    val ship = TopicModel("ship", segmentBytes = 1_073_741_824, retentionMs = 604_800_000, retentionBytes = null, batchSize = 16_384, 2)
    createTopic(pay, listOf("segment.bytes=65536", "retention.ms=-1", "retention.bytes=4000000"))
    createTopic(ship, emptyList())
    val names = listOf("p1", "p2", "p3", "p4")
    names.forEach(::initProducer)
    val restartAt = List(6) { random.nextInt(options.batches / 10, options.batches) }.sorted().toMutableList()
    while (batchList.size < options.batches) {
        if (restartAt.isNotEmpty() && batchList.size >= restartAt.first()) {
            restartAt.removeFirst()
            val restarting = producers.getValue(pick(names))
            repeat(random.nextInt(2, 6)) { createBatch(restarting, pay, random.nextInt(2), largeMessages()) }
            initProducer(restarting.name)
        }
        val producer = producers.getValue(pick(names))
        val topic = if (chance(0.5)) pay else ship
        val partition = random.nextInt(2)
        createBatch(producer, topic, partition, largeMessages())
        if (chance(0.1)) createBatch(producer, topic, partition, largeMessages())
        deliverLarge()
        if (chance(0.65)) deliverLarge()
        if (chance(0.0137)) produce(pick(topics), Message(if (chance(0.5)) null else "k${random.nextInt(100)}", "v${random.nextInt(1000)}"))
        if (chance(0.0275)) tick(random.nextLong(1, 100))
        if (chance(0.0275)) {
            val target = pick(topics)
            val partitionIndex = random.nextInt(2)
            val log = target.partitions[partitionIndex]
            val offset = (log.logEndOffset - random.nextLong(0, 20)).coerceAtLeast(log.logStartOffset)
            emit("FETCH ${target.name} $partitionIndex $offset ${random.nextInt(1, 3)}")
        }
        if (chance(0.0066)) emit("OFFSETS ${pick(topics).name} ${random.nextInt(2)}")
        if (chance(0.0014)) emit("PRODUCERS ${pick(topics).name} ${random.nextInt(2)}")
    }
}

if (largeScale) runLarge() else runNormal()
output.flush()
