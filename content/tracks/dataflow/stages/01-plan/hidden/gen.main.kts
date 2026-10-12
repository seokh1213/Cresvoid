import java.io.BufferedWriter
import java.io.OutputStreamWriter
import kotlin.random.Random

class Options(val rows: Int, val keys: Int, val commands: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--rows" to "120", "--keys" to "30", "--commands" to "140", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        rows = values.getValue("--rows").toInt(),
        keys = values.getValue("--keys").toInt(),
        commands = values.getValue("--commands").toInt(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.rows >= 100_000
val output = BufferedWriter(OutputStreamWriter(System.out, Charsets.UTF_8), 1 shl 16)

fun emit(line: String) {
    output.write(line)
    output.write("\n")
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun skewedIndex(size: Int): Int = (size * random.nextDouble() * random.nextDouble()).toInt().coerceIn(0, size - 1)

fun command(tokens: List<String>): String {
    if (largeScale) return tokens.joinToString(" ")
    val line = tokens.joinToString("") { token -> token + if (chance(0.06)) "  " else " " }.trimEnd(' ')
    return (if (chance(0.04)) "  " else "") + line + (if (chance(0.04)) " " else "")
}

enum class Kind { NUMBER, LIST }

class DatasetModel(val name: String, val partitions: Int, val weight: Int)

class PipelineModel(val name: String, val kind: Kind, val lineage: Int, val wide: Boolean = false)

val KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_"
val COMMON_WORDS = listOf("apple", "kiwi", "banana", "cart", "home", "a", "b", "ab", "abc", "a1", "b2", "z", "x_y", "0", "9z", "_k")
val RARE_KEYS = listOf(
    "polygenelubricants",
    "null",
    "_",
    "zzzzzzzzzzzzzzzzzzzzzzzz",
    "a23456789012345678901234",
    "0000",
    "a_",
    "aa",
    "apple_pie",
    "banana2",
)
val BAD_KEYS = listOf("Apple", "NULLX", "nul l", "a-b", "a23456789012345678901234x", "é", "A", "Null", "k.1")
val NAME_POOL = listOf("sales", "visits", "clicks", "orders", "logs", "events", "users", "items", "views", "words")
val BAD_NAMES = listOf("Sales", "1abc", "_x", "a-b", "a23456789012345678901234567890123", "sales!", "SALES", "é")
val PARTITION_CHOICES = listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 6, 7, 8, 12)
val COMPARISONS = listOf("=", "!=", "<", "<=", ">", ">=")
val REDUCERS = listOf("sum", "min", "max")
val SUMMARIES = listOf("sum", "min", "max", "count")

fun randomKey(minLength: Int, maxLength: Int): String =
    (1..random.nextInt(minLength, maxLength + 1)).map { KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)] }.joinToString("")

val commonKeys: List<String> = if (largeScale) {
    List(options.keys) { randomKey(4, 12) }
} else {
    (COMMON_WORDS + List(maxOf(0, options.keys - COMMON_WORDS.size)) { if (chance(0.15)) randomKey(10, 24) else randomKey(1, 5) })
        .take(maxOf(options.keys, 1))
}

fun drawKey(): String = when {
    chance(if (largeScale) 0.01 else 0.08) -> "NULL"
    largeScale -> commonKeys[random.nextInt(commonKeys.size)]
    chance(0.1) -> pick(RARE_KEYS)
    else -> commonKeys[skewedIndex(commonKeys.size)]
}

fun filterKey(): String = if (chance(0.7)) drawKey().takeIf { it != "NULL" } ?: "a" else pick(RARE_KEYS)

fun drawValue(): Long = when {
    chance(0.05) -> pick(listOf(1_000_000_000L, -1_000_000_000L, 0L, 999_999_999L))
    chance(0.5) -> random.nextLong(-5, 20)
    else -> random.nextLong(-1000, 100_000)
}

val datasets = mutableListOf<DatasetModel>()
val pipelines = mutableListOf<PipelineModel>()
var pipelineCounter = 0

fun isTaken(name: String): Boolean = datasets.any { it.name == name } || pipelines.any { it.name == name }

fun rowLine(partitions: Int): String = command(listOf(random.nextInt(partitions).toString(), drawKey(), drawValue().toString()))

fun faultyRowLine(partitions: Int): String {
    val partition = random.nextInt(partitions).toString()
    val key = drawKey()
    val value = drawValue().toString()
    return when (random.nextInt(12)) {
        0 -> "$partitions $key $value"
        1 -> "-1 $key $value"
        2 -> "0$partition $key $value"
        3 -> "$partition ${pick(BAD_KEYS)} $value"
        4 -> "$partition $key ${pick(listOf("1000000001", "-1000000001", "1e3", "+5", "-0", "007", "x", "99999999999999999999"))}"
        5 -> "$partition $key"
        6 -> "$partition $key $value 1"
        7 -> "$partition\t$key $value"
        8 -> "$partition $key $value\r"
        9 -> "+$partition $key $value"
        10 -> pick(listOf("NULL", "END END", "ENDX", "DATA x 1", "0 a 1 |"))
        else -> "$partition NULL ${pick(listOf("NULL", "-", "--1"))}"
    }
}

fun emitBlock(header: String, rowLines: List<String>) {
    emit(header)
    for (line in rowLines) {
        if (!largeScale && chance(0.03)) emit(pick(listOf("", "   ")))
        emit(line)
    }
    emit(if (!largeScale && chance(0.1)) pick(listOf(" END", "END  ", "  END  ")) else "END")
}

fun createDataset(name: String, partitions: Int, rows: Int, weight: Int = 1) {
    emitBlock(command(listOf("DATA", name, partitions.toString())), List(rows) { rowLine(partitions) })
    if (isTaken(name)) return
    datasets += DatasetModel(name, partitions, weight)
}

fun appendRows(dataset: DatasetModel, rows: Int) {
    emitBlock(command(listOf("APPEND", dataset.name)), List(rows) { rowLine(dataset.partitions) })
}

fun rowsWithFault(partitions: Int): List<String> {
    val rows = List(random.nextInt(0, 4)) { rowLine(partitions) }.toMutableList()
    rows.add(random.nextInt(rows.size + 1), faultyRowLine(partitions))
    if (chance(0.4)) rows.add(faultyRowLine(partitions))
    return rows
}

fun faultyBlock() {
    val dataset = pick(datasets)
    val someRows = { List(random.nextInt(0, 3)) { rowLine(dataset.partitions) } }
    when (random.nextInt(9)) {
        0 -> emitBlock(command(listOf("DATA", pick(datasets).name, "2")), someRows())
        1 -> emitBlock(command(listOf("DATA", pick(BAD_NAMES), "2")), someRows())
        2 -> emitBlock(command(listOf("DATA", "fresh${random.nextInt(100)}", pick(listOf("0", "1001", "01", "-1", "x", "2 3")))), someRows())
        3 -> emitBlock("DATA", someRows())
        4 -> emitBlock(command(listOf("APPEND", pick(listOf("nope", "missing", "sale")))), someRows())
        5 -> emitBlock(command(listOf("APPEND", pipelines.randomOrNull(random)?.name ?: "nope")), someRows())
        6 -> emitBlock(command(listOf("APPEND", dataset.name, "extra")), someRows())
        7 -> emitBlock(command(listOf("APPEND", dataset.name)), rowsWithFault(dataset.partitions))
        else -> {
            val partitions = pick(PARTITION_CHOICES)
            emitBlock(command(listOf("DATA", "bad${random.nextInt(1000)}", partitions.toString())), rowsWithFault(partitions))
        }
    }
}

fun partitionCount(): Int = when {
    largeScale -> random.nextInt(1, 5)
    chance(0.05) -> pick(listOf(50, 200, 1000))
    else -> random.nextInt(1, 7)
}

fun operand(): Long = when {
    chance(0.08) -> pick(listOf(1_000_000_000L, -1_000_000_000L))
    chance(0.1) -> 0L
    else -> random.nextLong(-10, 50)
}

fun numberTransform(keyedPartitions: Int?): List<String> {
    val partitions = if (keyedPartitions != null && chance(0.35)) keyedPartitions else partitionCount()
    val prefixLength = if (largeScale) random.nextInt(8, 25) else random.nextInt(1, 25)
    return when (random.nextInt(if (largeScale) 10 else 12)) {
        0 -> listOf("map", "value", pick(listOf("add", "mul")), operand().toString())
        1 -> listOf("filter", "value", pick(COMPARISONS), operand().toString())
        2 -> listOf("filter", "key", pick(listOf("=", "!=")), filterKey())
        3, 4, 5 -> listOf("reduceByKey", pick(REDUCERS), partitions.toString())
        6, 7 -> listOf("groupByKey", partitions.toString())
        8 -> listOf("coalesce", (if (chance(0.5)) partitions else random.nextInt(1, 10)).toString())
        9 -> listOf("map", "key", "prefix", prefixLength.toString())
        10 -> listOf("repartition", partitionCount().toString())
        else -> listOf("map", "key", "prefix", random.nextInt(1, 4).toString())
    }
}

fun listTransform(): List<String> = when (random.nextInt(7)) {
    0, 1, 2 -> listOf("map", "value", pick(SUMMARIES))
    3 -> listOf("filter", "key", pick(listOf("=", "!=")), pick(COMMON_WORDS))
    4 -> listOf("coalesce", random.nextInt(1, 6).toString())
    5 -> listOf("repartition", random.nextInt(1, 6).toString())
    else -> listOf("map", "key", "prefix", random.nextInt(1, 6).toString())
}

fun outputKind(transform: List<String>, input: Kind): Kind = when {
    transform[0] == "groupByKey" -> Kind.LIST
    transform[0] == "map" && transform[1] == "value" && transform[2] in SUMMARIES -> Kind.NUMBER
    else -> input
}

fun weightedDataset(): DatasetModel {
    var ticket = random.nextInt(datasets.sumOf { it.weight })
    return datasets.first { dataset -> (ticket < dataset.weight).also { ticket -= dataset.weight } }
}

fun pipeTokens(name: String, transforms: List<List<String>>): List<String> =
    listOf("PIPE", name, "=") + transforms.flatMapIndexed { index, transform -> (if (index > 0) listOf("|") else emptyList()) + transform }

fun definePipeline(name: String = "p${pipelineCounter++}") {
    val sources = pipelines.filter { it.lineage < 40 }
    val parent = if (sources.isNotEmpty() && chance(0.35)) pick(sources) else null
    var kind = parent?.kind ?: Kind.NUMBER
    var keyedPartitions: Int? = null
    val transforms = mutableListOf(listOf("source", parent?.name ?: weightedDataset().name))
    repeat(random.nextInt(1, if (largeScale) 4 else 6)) {
        val transform = if (kind == Kind.NUMBER) numberTransform(keyedPartitions) else listTransform()
        if (transform[0] == "reduceByKey" || transform[0] == "groupByKey") keyedPartitions = transform.last().toInt()
        transforms += transform
        kind = outputKind(transform, kind)
    }
    emit(command(pipeTokens(name, transforms)))
    if (!isTaken(name)) pipelines += PipelineModel(name, kind, (parent?.lineage ?: 1) + transforms.size - 1)
}

fun faultyPipe() {
    val dataset = pick(datasets).name
    val fresh = "t${random.nextInt(100)}"
    val tokens = when (random.nextInt(14)) {
        0 -> return definePipeline(pick(datasets.map { it.name } + pipelines.map { it.name }))
        1 -> listOf("PIPE", fresh, "=", "source", pick(listOf("nope", "missing", "x${random.nextInt(100)}")))
        2 -> listOf("PIPE", "self", "=", "source", "self")
        3 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "groupByKey", "2", "|") + pick(listOf(listOf("filter", "value", ">", "1"), listOf("map", "value", "add", "1")))
        4 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "map", "value", pick(SUMMARIES))
        5 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "groupByKey", "3", "|") + pick(listOf(listOf("reduceByKey", "sum", "2"), listOf("groupByKey", "2")))
        6 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "map", "value", "div", "2")
        7 -> listOf("PIPE", fresh, "=", "source", "$dataset|reduceByKey", "sum", "2")
        8 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "|", "coalesce", "2")
        9 -> listOf("PIPE", fresh, "=", "source", dataset, "|")
        10 -> listOf("PIPE", pick(BAD_NAMES), "=", "source", dataset)
        11 -> listOf("PIPE", fresh, "source", dataset)
        12 -> listOf("PIPE", fresh, "=", "source", dataset, "|") + pick(
            listOf(
                "reduceByKey sum 0", "groupByKey 1001", "coalesce 01", "map key prefix 25", "map key prefix 0",
                "filter value >= 1000000001", "filter key = NULL", "filter key == a", "repartition", "source a b",
                "map value add", "reduceByKey avg 2", "map value mul 1e3", "filter key = Apple", "map value count 1",
            ),
        ).split(" ")
        else -> listOf("PIPE", fresh, "=", "source", dataset, "|", "source", dataset)
    }
    emit(command(tokens))
}

fun existingName(): String = if (pipelines.isNotEmpty() && chance(0.7)) pick(pipelines).name else pick(datasets).name

fun runCount(): String? = when {
    largeScale -> random.nextInt(0, 21).toString()
    chance(0.5) -> null
    chance(0.3) -> random.nextInt(0, 4).toString()
    chance(0.5) -> pick(listOf("0", "1000000000", "1"))
    else -> random.nextInt(1, 20).toString()
}

fun emitRun() {
    val wide = pipelines.filter { it.wide }
    val name = if (largeScale && wide.isNotEmpty() && chance(0.95)) pick(wide).name else existingName()
    emit(command(listOfNotNull("RUN", name, runCount())))
}

fun defineWidePipeline() {
    val name = "w${pipelineCounter++}"
    val transforms = mutableListOf(listOf("source", if (chance(0.75)) "facts" else "events"))
    if (chance(0.4)) transforms += listOf("map", "value", pick(listOf("add", "mul")), operand().toString())
    val partitions = if (chance(0.85)) "1" else "2"
    val kind = if (chance(0.6)) {
        transforms += listOf("reduceByKey", pick(REDUCERS), partitions)
        if (chance(0.3)) transforms += listOf("filter", "value", pick(COMPARISONS), operand().toString())
        Kind.NUMBER
    } else {
        transforms += listOf("groupByKey", partitions)
        if (chance(0.5)) transforms += listOf("map", "value", pick(SUMMARIES))
        if (transforms.last()[0] == "map") Kind.NUMBER else Kind.LIST
    }
    emit(command(pipeTokens(name, transforms)))
    pipelines += PipelineModel(name, kind, transforms.size, wide = true)
}

fun faultyAction() {
    val verb = pick(listOf("RUN", "PLAN"))
    val tokens = when (random.nextInt(8)) {
        0 -> listOf(verb, pick(listOf("nope", "missing", "zz")))
        1 -> listOf(verb, pick(BAD_NAMES))
        2 -> listOf(verb)
        3 -> listOf("PLAN", existingName(), "1")
        4 -> listOf("RUN", existingName(), pick(listOf("-1", "1000000001", "01", "x", "-0", "+1")))
        5 -> listOf("RUN", existingName(), "1", "2")
        6 -> listOf(verb, "Sales")
        else -> listOf("RUN", pick(listOf("nope", "zz")), "3")
    }
    emit(command(tokens))
}

fun unknownCommand() {
    emit(
        pick(
            listOf(
                "run sales", "Run sales", "FOO", "END", "plan x", "RUN\tsales", "PIPE\tp = source a",
                "pipe p = source sales", "#", "select", "set memory 1", "profile p", "--", "data x 1",
            ),
        ),
    )
}

fun emitNormalSession() {
    val names = NAME_POOL.shuffled(random).take(random.nextInt(2, 5))
    for ((index, name) in names.withIndex()) {
        val rows = if (index == 0) maxOf(1, options.rows / (2 * names.size)) else random.nextInt(0, maxOf(1, options.rows / names.size))
        createDataset(name, if (chance(0.06)) 1000 else pick(PARTITION_CHOICES), rows)
    }
    repeat(options.commands) {
        if (chance(0.03)) emit(pick(listOf("", "  ", "   ")))
        val roll = random.nextInt(100)
        when {
            roll < 10 -> appendRows(pick(datasets), random.nextInt(0, 8))
            roll < 14 -> faultyBlock()
            roll < 34 -> definePipeline()
            roll < 42 -> faultyPipe()
            roll < 54 -> emit(command(listOf("PLAN", existingName())))
            roll < 78 -> emitRun()
            roll < 86 -> faultyAction()
            roll < 89 -> unknownCommand()
            roll < 92 -> createDataset(pick(NAME_POOL) + random.nextInt(10), pick(PARTITION_CHOICES), random.nextInt(0, 6))
            else -> emit(command(listOf("RUN", pick(datasets).name, runCount() ?: "5")))
        }
    }
}

fun emitLargeSession() {
    val appendedRows = options.rows / 7
    val initialRows = options.rows - appendedRows
    createDataset("facts", 4, initialRows * 70 / 100, weight = 70)
    createDataset("events", 8, initialRows * 20 / 100, weight = 20)
    createDataset("small", 2, initialRows * 7 / 100, weight = 7)
    createDataset("tiny", 1, initialRows * 3 / 100, weight = 3)
    repeat(8) { defineWidePipeline() }
    val actions = (List(100) { "APPEND" } + List(12) { "WIDE" } + List(80) { "PIPE" } + List(50) { "PLAN" } + List(20) { "RUN" }).shuffled(random)
    for (action in actions) {
        when (action) {
            "APPEND" -> appendRows(weightedDataset(), appendedRows / 100)
            "WIDE" -> defineWidePipeline()
            "PIPE" -> definePipeline()
            "PLAN" -> emit(command(listOf("PLAN", existingName())))
            else -> emitRun()
        }
    }
}

if (largeScale) emitLargeSession() else emitNormalSession()
output.flush()
