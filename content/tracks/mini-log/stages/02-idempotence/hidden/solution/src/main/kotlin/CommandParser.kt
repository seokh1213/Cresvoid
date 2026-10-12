private val INTEGER_FORMAT = Regex("0|[1-9][0-9]*")
private val NAME_FORMAT = Regex("[a-z][a-z0-9._-]{0,31}")
private val KEY_FORMAT = Regex("[A-Za-z0-9._:-]{1,64}")
private val VALUE_FORMAT = Regex("[A-Za-z0-9._:-]{1,256}")
private const val NO_KEY = "-"
private const val UNLIMITED = "-1"

private val PARTITION_COUNT_RANGE = 1L..1_000L
private val PARTITION_RANGE = 0L..999L
private val OFFSET_RANGE = 0L..Long.MAX_VALUE
private val MAX_RECORDS_RANGE = 1L..1_000L
private val TICK_RANGE = 1L..1_000_000_000L
private val RETENTION_RANGE = 0L..1_000_000_000_000_000L
private val BYTES_RANGE = 1L..1_073_741_824L
private val BATCH_ARGUMENT_COUNTS = 6..204

private val TOPIC_SETTINGS = setOf("retention.ms", "retention.bytes", "segment.bytes", "batch.size")

fun parseCommand(line: String): Command {
    val words = line.split(' ').filter(String::isNotEmpty)
    val arguments = Arguments(words.drop(1))
    return when (words.first()) {
        "CREATE" -> arguments.read(2..Int.MAX_VALUE) {
            Command.CreateTopic(
                topic = name(0),
                partitionCount = number(1, PARTITION_COUNT_RANGE).toInt(),
                config = topicConfig(settings(from = 2, TOPIC_SETTINGS)),
            )
        }
        "PARTITIONS" -> arguments.read(2..2) {
            Command.AddPartitions(topic = name(0), partitionCount = number(1, PARTITION_COUNT_RANGE).toInt())
        }
        "PRODUCE" -> arguments.read(3..3) { Command.Produce(topic = name(0), message = Message(key(1), value(2))) }
        "FETCH" -> arguments.read(4..4) {
            Command.Fetch(
                target = topicPartition(0),
                offset = number(2, OFFSET_RANGE),
                maxRecords = number(3, MAX_RECORDS_RANGE).toInt(),
            )
        }
        "OFFSETS" -> arguments.read(2..2) { Command.ShowOffsets(topicPartition(0)) }
        "SEGMENTS" -> arguments.read(2..2) { Command.ShowSegments(topicPartition(0)) }
        "TICK" -> arguments.read(1..1) { Command.Tick(number(0, TICK_RANGE)) }
        "INIT" -> arguments.read(1..1) { Command.InitProducer(name(0)) }
        "BATCH" -> arguments.read(BATCH_ARGUMENT_COUNTS) {
            Command.CreateBatch(
                producer = name(0),
                batch = name(1),
                target = topicPartition(2),
                messages = messages(from = 4),
            )
        }
        "DELIVER" -> arguments.read(1..1) { Command.Deliver(name(0)) }
        "PRODUCERS" -> arguments.read(2..2) { Command.ShowProducers(topicPartition(0)) }
        else -> reject(ErrorCode.UNKNOWN_COMMAND)
    }
}

private class Arguments(private val words: List<String>) {
    fun <T> read(counts: IntRange, build: Arguments.() -> T): T {
        if (words.size !in counts) reject(ErrorCode.BAD_ARGUMENT)
        return build()
    }

    fun name(index: Int): String = matching(words[index], NAME_FORMAT)

    fun key(index: Int): String? = words[index].takeUnless { it == NO_KEY }?.let { matching(it, KEY_FORMAT) }

    fun value(index: Int): String = matching(words[index], VALUE_FORMAT)

    fun number(index: Int, range: LongRange): Long = parseNumber(words[index], range)

    fun topicPartition(index: Int): TopicPartition =
        TopicPartition(topic = name(index), partition = number(index + 1, PARTITION_RANGE).toInt())

    fun messages(from: Int): List<Message> {
        if ((words.size - from) % 2 != 0) reject(ErrorCode.BAD_ARGUMENT)
        return (from until words.size step 2).map { Message(key(it), value(it + 1)) }
    }

    fun settings(from: Int, known: Set<String>): Map<String, String> {
        val valuesByName = HashMap<String, String>()
        for (word in words.drop(from)) {
            val separator = word.indexOf('=')
            if (separator < 0) reject(ErrorCode.BAD_ARGUMENT)
            val name = word.substring(0, separator)
            if (name !in known || name in valuesByName) reject(ErrorCode.BAD_ARGUMENT)
            valuesByName[name] = word.substring(separator + 1)
        }
        return valuesByName
    }
}

private fun matching(word: String, format: Regex): String =
    word.takeIf(format::matches) ?: reject(ErrorCode.BAD_ARGUMENT)

private fun parseNumber(word: String, range: LongRange): Long =
    matching(word, INTEGER_FORMAT).toLongOrNull()?.takeIf { it in range } ?: reject(ErrorCode.BAD_ARGUMENT)

private fun retentionLimit(word: String): Long? = if (word == UNLIMITED) null else parseNumber(word, RETENTION_RANGE)

private fun topicConfig(settings: Map<String, String>): TopicConfig {
    val retentionMs = settings["retention.ms"]
    return TopicConfig(
        retentionMs = if (retentionMs == null) DEFAULT_RETENTION_MS else retentionLimit(retentionMs),
        retentionBytes = settings["retention.bytes"]?.let(::retentionLimit),
        segmentBytes = settings["segment.bytes"]?.let { parseNumber(it, BYTES_RANGE) } ?: DEFAULT_SEGMENT_BYTES,
        batchSize = settings["batch.size"]?.let { parseNumber(it, BYTES_RANGE) } ?: DEFAULT_BATCH_SIZE,
    )
}
