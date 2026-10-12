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

enum class Kind { NUMBER, LIST, PAIR }

class DatasetModel(val name: String, val partitions: Int)

class PipelineModel(val name: String, val kind: Kind, val lineage: Int, val hashPartitions: Int?)

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
val SIZE_LIMIT = 1_000_000_000_000_000L

fun randomKey(minLength: Int, maxLength: Int): String =
    (1..random.nextInt(minLength, maxLength + 1)).map { KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)] }.joinToString("")

val commonKeys: List<String> = if (largeScale) {
    generateSequence { randomKey(4, 12) }.distinct().take(options.keys).toList()
} else {
    (COMMON_WORDS + List(maxOf(0, options.keys - COMMON_WORDS.size)) { if (chance(0.15)) randomKey(10, 24) else randomKey(1, 5) })
        .take(maxOf(options.keys, 1))
}

fun drawKey(): String = when {
    chance(0.08) -> "NULL"
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

fun createDataset(name: String, partitions: Int, rows: Int) {
    emitBlock(command(listOf("DATA", name, partitions.toString())), List(rows) { rowLine(partitions) })
    if (!isTaken(name)) datasets += DatasetModel(name, partitions)
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

fun partitionCount(): Int = if (chance(0.05)) pick(listOf(50, 200, 1000)) else random.nextInt(1, 7)

fun operand(): Long = when {
    chance(0.08) -> pick(listOf(1_000_000_000L, -1_000_000_000L))
    chance(0.1) -> 0L
    else -> random.nextLong(-10, 50)
}

class Step(val tokens: List<String>, val kind: Kind, val hashPartitions: Int?, val lineage: Int)

fun joinStep(hashPartitions: Int?): Step {
    val candidates = datasets.map { it.name to null } + pipelines.filter { it.kind == Kind.NUMBER && it.lineage < 20 }.map { it.name to it.hashPartitions }
    val (right, rightPartitions) = pick(candidates)
    val partitions = when {
        hashPartitions != null && chance(0.4) -> hashPartitions
        rightPartitions != null && chance(0.4) -> rightPartitions
        else -> random.nextInt(1, 7)
    }
    val tokens = listOf("join", right, partitions.toString()) + if (chance(0.3)) listOf("nullsafe") else emptyList()
    val rightLineage = pipelines.firstOrNull { it.name == right }?.lineage ?: 1
    return Step(tokens, Kind.PAIR, null, 1 + rightLineage)
}

fun numberStep(hashPartitions: Int?): Step {
    val partitions = if (hashPartitions != null && chance(0.35)) hashPartitions else partitionCount()
    return when (random.nextInt(14)) {
        0 -> Step(listOf("map", "value", pick(listOf("add", "mul")), operand().toString()), Kind.NUMBER, null, 1)
        1 -> Step(listOf("filter", "value", pick(COMPARISONS), operand().toString()), Kind.NUMBER, hashPartitions, 1)
        2 -> Step(listOf("filter", "key", pick(listOf("=", "!=")), filterKey()), Kind.NUMBER, hashPartitions, 1)
        3, 4 -> Step(listOf("reduceByKey", pick(REDUCERS), partitions.toString()), Kind.NUMBER, partitions, 1)
        5 -> Step(listOf("groupByKey", partitions.toString()), Kind.LIST, partitions, 1)
        6 -> Step(listOf("coalesce", (if (chance(0.5)) partitions else random.nextInt(1, 10)).toString()), Kind.NUMBER, null, 1)
        7 -> Step(listOf("map", "key", "prefix", random.nextInt(1, 25).toString()), Kind.NUMBER, null, 1)
        8 -> Step(listOf("repartition", partitionCount().toString()), Kind.NUMBER, null, 1)
        else -> joinStep(hashPartitions)
    }
}

fun listStep(hashPartitions: Int?): Step = when (random.nextInt(7)) {
    0, 1, 2 -> Step(listOf("map", "value", pick(SUMMARIES)), Kind.NUMBER, null, 1)
    3 -> Step(listOf("filter", "key", pick(listOf("=", "!=")), pick(COMMON_WORDS)), Kind.LIST, hashPartitions, 1)
    4 -> Step(listOf("coalesce", random.nextInt(1, 6).toString()), Kind.LIST, null, 1)
    5 -> Step(listOf("repartition", random.nextInt(1, 6).toString()), Kind.LIST, null, 1)
    else -> Step(listOf("map", "key", "prefix", random.nextInt(1, 6).toString()), Kind.LIST, null, 1)
}

fun pairStep(): Step = when (random.nextInt(8)) {
    0, 1, 2, 3 -> Step(listOf("map", "value", pick(REDUCERS)), Kind.NUMBER, null, 1)
    4 -> Step(listOf("filter", "key", pick(listOf("=", "!=")), filterKey()), Kind.PAIR, null, 1)
    5 -> Step(listOf("coalesce", random.nextInt(1, 6).toString()), Kind.PAIR, null, 1)
    6 -> Step(listOf("repartition", random.nextInt(1, 6).toString()), Kind.PAIR, null, 1)
    else -> Step(listOf("map", "key", "prefix", random.nextInt(1, 6).toString()), Kind.PAIR, null, 1)
}

fun pipeTokens(name: String, transforms: List<List<String>>): List<String> =
    listOf("PIPE", name, "=") + transforms.flatMapIndexed { index, transform -> (if (index > 0) listOf("|") else emptyList()) + transform }

fun definePipeline(name: String = "p${pipelineCounter++}") {
    val sources = pipelines.filter { it.lineage < 30 }
    val parent = if (sources.isNotEmpty() && chance(0.35)) pick(sources) else null
    var kind = parent?.kind ?: Kind.NUMBER
    var hashPartitions = parent?.hashPartitions
    var lineage = parent?.lineage ?: 1
    val transforms = mutableListOf(listOf("source", parent?.name ?: pick(datasets).name))
    repeat(random.nextInt(1, 6)) {
        val step = when (kind) {
            Kind.NUMBER -> numberStep(hashPartitions)
            Kind.LIST -> listStep(hashPartitions)
            Kind.PAIR -> pairStep()
        }
        transforms += step.tokens
        kind = step.kind
        hashPartitions = step.hashPartitions
        lineage += step.lineage
    }
    emit(command(pipeTokens(name, transforms)))
    if (!isTaken(name)) pipelines += PipelineModel(name, kind, lineage, hashPartitions)
}

fun pipelineOfKind(kind: Kind): String? = pipelines.filter { it.kind == kind && it.lineage < 20 }.randomOrNull(random)?.name

fun faultyPipe() {
    val dataset = pick(datasets).name
    val fresh = "t${random.nextInt(100)}"
    val tokens = when (random.nextInt(22)) {
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
        13 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "source", dataset)
        14 -> listOf("PIPE", fresh, "=", "source", dataset, "|") + pick(
            listOf("join $dataset", "join $dataset 0", "join $dataset 1001", "join $dataset 2 null_safe", "join $dataset 2 nullsafe x", "join Users 2", "join $dataset 02", "join $dataset nullsafe 2"),
        ).split(" ")
        15 -> listOf("PIPE", fresh, "=", "join", dataset, "2")
        16 -> listOf("PIPE", fresh, "=", "source", pick(listOf("nope", dataset)), "|", "join", "nope", "2", "|", "join", "zzz", "2")
        17 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "groupByKey", "2", "|", "join", dataset, "2")
        18 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "join", pipelineOfKind(pick(listOf(Kind.LIST, Kind.PAIR))) ?: dataset, "2")
        19 -> listOf("PIPE", fresh, "=", "source", dataset, "|", "join", dataset, "2", "|", "join", dataset, "3")
        20 -> listOf("PIPE", fresh, "=", "source", pipelineOfKind(Kind.PAIR) ?: dataset, "|") + pick(
            listOf("map value count", "filter value > 1", "reduceByKey sum 2", "groupByKey 2", "map value add 1"),
        ).split(" ")
        else -> listOf("PIPE", fresh, "=", "source", dataset, "|", "join", fresh, "2")
    }
    emit(command(tokens))
}

fun existingName(): String = if (pipelines.isNotEmpty() && chance(0.7)) pick(pipelines).name else pick(datasets).name

fun runCount(): String? = when {
    chance(0.5) -> null
    chance(0.3) -> random.nextInt(0, 4).toString()
    chance(0.5) -> pick(listOf("0", "1000000000", "1"))
    else -> random.nextInt(1, 20).toString()
}

fun emitRun() {
    emit(command(listOfNotNull("RUN", existingName(), runCount())))
}

fun faultyAction() {
    val verb = pick(listOf("RUN", "PLAN", "PROFILE"))
    val tokens = when (random.nextInt(9)) {
        0 -> listOf(verb, pick(listOf("nope", "missing", "zz")))
        1 -> listOf(verb, pick(BAD_NAMES))
        2 -> listOf(verb)
        3 -> listOf(pick(listOf("PLAN", "PROFILE")), existingName(), "1")
        4 -> listOf("RUN", existingName(), pick(listOf("-1", "1000000001", "01", "x", "-0", "+1")))
        5 -> listOf("RUN", existingName(), "1", "2")
        6 -> listOf(verb, "Sales")
        7 -> listOf("PROFILE", existingName(), existingName())
        else -> listOf("RUN", pick(listOf("nope", "zz")), "3")
    }
    emit(command(tokens))
}

fun unknownCommand() {
    emit(
        pick(
            listOf(
                "run sales", "Run sales", "FOO", "END", "plan x", "RUN\tsales", "PIPE\tp = source a",
                "pipe p = source sales", "#", "select", "set memory 1", "profile p", "--", "data x 1", "JOIN a b 2",
            ),
        ),
    )
}

fun settingValue(name: String): String = when (name) {
    "memory" -> pick(listOf("-1", random.nextLong(5, 60).toString(), random.nextLong(60, 2000).toString(), "1000000000000", SIZE_LIMIT.toString()))
    "broadcastThreshold" -> pick(listOf("-1", "-1", "0", random.nextLong(10, 120).toString(), random.nextLong(120, 600).toString(), "10485760", SIZE_LIMIT.toString()))
    "skewFactor" -> pick(listOf("1", "1", "1", "2", "3", random.nextLong(1, 10).toString(), "1000", "5"))
    "skewThreshold" -> pick(listOf("0", "0", random.nextLong(0, 60).toString(), random.nextLong(0, 60).toString(), random.nextLong(60, 300).toString(), "268435456", SIZE_LIMIT.toString()))
    else -> pick(listOf("1", random.nextLong(1, 40).toString(), random.nextLong(1, 40).toString(), random.nextLong(40, 300).toString(), "67108864", SIZE_LIMIT.toString()))
}

fun setSetting() {
    val name = pick(listOf("memory", "broadcastThreshold", "broadcastThreshold", "skewFactor", "skewThreshold", "advisorySize"))
    emit(command(listOf("SET", name, settingValue(name))))
}

fun faultySet() {
    val tokens = when (random.nextInt(10)) {
        0 -> listOf("SET", "memory", pick(listOf("0", "-2", "007", "1000000000000001", "abc", "-0", "+5", "1e3", "99999999999999999999")))
        1 -> listOf("SET", pick(listOf("memory", "skewFactor", "broadcastThreshold")))
        2 -> listOf("SET", "memory", "5", "6")
        3 -> listOf("SET")
        4 -> listOf("SET", pick(listOf("cache", "Memory", "mem", "memoryLimit", "parallelism", "BroadcastThreshold", "skew", "advisory")), "5")
        5 -> listOf("SET", "broadcastThreshold", pick(listOf("-2", "1000000000000001", "abc", "-0", "007")))
        6 -> listOf("SET", "skewFactor", pick(listOf("0", "1001", "-1", "2.5")))
        7 -> listOf("SET", "skewThreshold", pick(listOf("-1", "1000000000000001", "x")))
        8 -> listOf("SET", "advisorySize", pick(listOf("0", "-1", "1000000000000001")))
        else -> listOf("SET", pick(listOf("cache", "spill")), "abc")
    }
    emit(command(tokens))
}

fun registerScenario(name: String, transforms: List<List<String>>, kind: Kind) {
    emit(command(pipeTokens(name, transforms)))
    pipelines += PipelineModel(name, kind, transforms.size + 1, null)
}

fun emitSkewScenario() {
    val name = "s${pipelineCounter++}"
    emit(command(listOf("SET", "broadcastThreshold", "-1")))
    emit(command(listOf("SET", "skewFactor", pick(listOf("1", "1", "2")))))
    emit(command(listOf("SET", "skewThreshold", random.nextLong(0, 30).toString())))
    emit(command(listOf("SET", "advisorySize", random.nextLong(1, 40).toString())))
    val transforms = mutableListOf(listOf("source", pick(datasets).name), listOf("join", pick(datasets).name, random.nextInt(2, 7).toString()))
    val kind = if (chance(0.5)) {
        transforms += listOf("map", "value", pick(REDUCERS))
        if (chance(0.5)) transforms += listOf("filter", "value", ">", random.nextLong(-5, 10).toString())
        Kind.NUMBER
    } else {
        Kind.PAIR
    }
    registerScenario(name, transforms, kind)
    emit(command(listOf("PROFILE", name)))
}

fun emitNormalSession() {
    val names = NAME_POOL.shuffled(random).take(random.nextInt(2, 5))
    for ((index, name) in names.withIndex()) {
        val rows = if (index == 0) maxOf(1, options.rows / (2 * names.size)) else random.nextInt(0, maxOf(1, options.rows / names.size))
        createDataset(name, if (chance(0.04)) 1000 else pick(PARTITION_CHOICES), rows)
    }
    repeat(options.commands) {
        if (chance(0.03)) emit(pick(listOf("", "  ", "   ")))
        val roll = random.nextInt(100)
        when {
            roll < 7 -> appendRows(pick(datasets), random.nextInt(0, 8))
            roll < 9 -> faultyBlock()
            roll < 27 -> definePipeline()
            roll < 33 -> faultyPipe()
            roll < 39 -> emit(command(listOf("PLAN", existingName())))
            roll < 52 -> emitRun()
            roll < 70 -> emit(command(listOf("PROFILE", existingName())))
            roll < 82 -> setSetting()
            roll < 86 -> faultySet()
            roll < 91 -> faultyAction()
            roll < 93 -> unknownCommand()
            roll < 94 -> createDataset(pick(NAME_POOL) + random.nextInt(10), pick(PARTITION_CHOICES), random.nextInt(0, 6))
            roll < 97 -> emitSkewScenario()
            else -> emit(command(listOf("PROFILE", pick(datasets).name)))
        }
    }
}

fun emitLargeSession() {
    val hotKeys = generateSequence { randomKey(3, 8) }.filter { it !in commonKeys }.distinct().take(6).toList()
    val dimensionRows = options.rows / 6
    val factRows = options.rows - dimensionRows
    emit("DATA facts 8")
    repeat(factRows) {
        val key = when {
            chance(0.002) -> "NULL"
            chance(0.08) -> pick(hotKeys)
            else -> commonKeys[random.nextInt(commonKeys.size)]
        }
        emit("${random.nextInt(8)} $key ${drawValue()}")
    }
    emit("END")
    emit("DATA dims 4")
    var emitted = 0
    var keyIndex = 0
    while (emitted < dimensionRows) {
        val key = when {
            emitted < 12 -> "NULL"
            emitted < 12 + hotKeys.size -> hotKeys[emitted - 12]
            else -> commonKeys[keyIndex++ % commonKeys.size]
        }
        val copies = if (key == "NULL") 1 else random.nextInt(1, 4)
        repeat(minOf(copies, dimensionRows - emitted)) {
            emit("${random.nextInt(4)} $key ${random.nextLong(0, 10_000)}")
            emitted++
        }
    }
    emit("END")
    listOf(
        "PIPE bc = source facts | join dims 8",
        "PIPE bl = source dims | filter value < 5000 | join facts 8",
        "PIPE fr = source facts | reduceByKey sum 8",
        "PIPE dr = source dims | reduceByKey max 8",
        "PIPE co = source fr | join dr 8 | map value sum",
        "PIPE half = source fr | join dims 8",
        "PIPE ns = source facts | filter value > 0 | join dims 4 nullsafe",
        "PIPE sm = source facts | join dims 4",
        "PIPE agg = source sm | map value sum | reduceByKey max 4",
        "PIPE hot = source facts | join dims 64 | map value max",
        "PIPE hotsum = source hot | reduceByKey sum 8",
        "PIPE chain = source facts | join dims 4 | map value min | join dr 8 | map value sum",
    ).forEach(::emit)
    val actions = listOf(
        "RUN bc ${random.nextInt(1, 21)}",
        "PROFILE bl",
        "RUN ns ${random.nextInt(1, 21)}",
        "PROFILE chain",
        "SET broadcastThreshold -1",
        "PROFILE sm",
        "RUN agg ${random.nextInt(1, 21)}",
        "PROFILE co",
        "RUN half ${random.nextInt(1, 21)}",
        "PROFILE ns",
        "SET skewFactor 1",
        "SET skewThreshold ${random.nextLong(150_000, 170_000)}",
        "SET advisorySize ${random.nextLong(20_000, 60_000)}",
        "PROFILE hot",
        "RUN hotsum ${random.nextInt(1, 21)}",
        "SET memory ${random.nextLong(200_000, 2_000_000)}",
        "PROFILE hotsum",
        "RUN chain ${random.nextInt(1, 21)}",
    )
    actions.forEach(::emit)
}

if (largeScale) emitLargeSession() else emitNormalSession()
output.flush()
