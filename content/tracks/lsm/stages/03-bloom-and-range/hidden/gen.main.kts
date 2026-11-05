import kotlin.random.Random

val stage = 3
val options = args.toList().chunked(2).associate { option ->
    require(option.size == 2 && option[0].startsWith("--")) { "Use --name value pairs" }
    option[0] to option[1]
}
val allowed = setOf("--seed", "--mode", "--commands", "--keys", "--capacity", "--writes", "--gets", "--compacts", "--ranges")
require(options.keys.all { it in allowed }) { "Unknown option" }
fun number(name: String, default: Int, bounds: IntRange): Int =
    (options[name]?.toIntOrNull() ?: if (name in options) error("Invalid $name") else default)
        .also { require(it in bounds) { "Out of range: $name" } }

val seed = number("--seed", 1, Int.MIN_VALUE..Int.MAX_VALUE)
val random = Random(seed)
val mode = options["--mode"] ?: "mixed"
require(mode == "mixed" || mode == "scale") { "Use --mode mixed or --mode scale" }
val output = System.out.bufferedWriter()
fun emit(line: String) { output.appendLine(line) }
fun wideKey(index: Int): String = "k" + index.toString(36).padStart(15, '0')

fun mixedInput() {
    val count = number("--commands", 2000, 250..200000)
    val keyCount = number("--keys", 80, 16..100000)
    val capacity = number("--capacity", 4, 1..4096)
    val familiar = listOf("a", "a0", "a10", "a2", "b", "q", "z", "apple", "user", "ghost", "zz", "az")
    val keys = familiar + List(keyCount - familiar.size) { index ->
        if (index % 2 == 0) wideKey(index) else "r" + random.nextLong().toULong().toString(36).take(15)
    }
    val values = listOf("A", "NEW", "OLD", "0", "_", "cat", "Q7_x", "Z".repeat(32), "r9K_".repeat(8))
    val commands = mutableListOf<String>()
    val malformed = mutableListOf(
        "RESET", "RESET 0", "RESET 4097", "RESET -1", "RESET +1", "RESET 1.0", "RESET 1e1",
        "RESET " + "9".repeat(512), "RESET 2 extra", "PUT a", "PUT a A extra", "PUT A A",
        "PUT " + "a".repeat(17) + " A", "PUT a " + "X".repeat(33), "PUT a bad!", "PUT a 한글",
        "DEL", "DEL a extra", "DEL a_", "GET", "GET A", "GET a extra", "FLUSH extra", "LAYOUT extra",
        "put a B", "UNKNOWN", "GET\u000ba", "RESET\u00a01"
    )
    if (stage >= 2) malformed += listOf("COMPACT extra", "METRICS extra")
    if (stage >= 3) malformed += listOf(
        "BGET", "BGET A", "BGET a extra", "READS extra", "RANGE a z", "RANGE a z 1 extra",
        "RANGE A z 0", "RANGE z a 1001", "RANGE z a -0", "RANGE a z +1", "RANGE a z 1.0",
        "RANGE a z " + "9".repeat(512), "RANGE a z 00x"
    )
    commands += listOf("GET a", "PUT a A", "DEL a", "FLUSH", "LAYOUT")
    if (stage >= 2) commands += listOf("COMPACT", "METRICS")
    if (stage >= 3) commands += listOf("BGET a", "READS", "RANGE a z 0")
    commands += listOf("RESET 4096", "PUT a A", "PUT a NEW", "DEL ghost", "LAYOUT", "FLUSH", "FLUSH")
    commands += "RESET " + "0".repeat(500) + "1"
    commands += listOf("PUT a OLD", "PUT b B", "PUT c C", "PUT d D", "PUT e E", "PUT f F", "PUT g G")
    if (stage >= 2) commands += listOf("COMPACT", "METRICS")
    commands += listOf("DEL a", "PUT h H", "PUT i I")
    if (stage >= 2) commands += "COMPACT"
    commands += listOf("GET a", "PUT a BACK", "GET a", "LAYOUT")
    commands += "RESET 2"
    commands += listOf("PUT a A", "PUT z Z")
    if (stage >= 3) commands += listOf("BGET q", "BGET b", "BGET a", "READS")
    commands += listOf("DEL a", "GET a")
    if (stage >= 3) commands += listOf("BGET a", "RANGE a z 1000", "RANGE a a 01", "RANGE z a 2", "RANGE a z " + "0".repeat(500))
    commands += malformed
    commands += listOf("GET z", "LAYOUT", "RESET $capacity")
    while (commands.size < count - 1) {
        val key = keys[random.nextInt(keys.size)]
        val line = when (random.nextInt(if (stage == 1) 12 else if (stage == 2) 17 else 25)) {
            in 0..3 -> "PUT $key ${values[random.nextInt(values.size)]}"
            4, 5 -> "DEL $key"
            6, 7 -> "GET $key"
            8 -> "FLUSH"
            9 -> malformed[random.nextInt(malformed.size)]
            10 -> "LAYOUT"
            11 -> "RESET ${listOf(1, 2, capacity, 4096)[random.nextInt(4)]}"
            12, 13, 14 -> "COMPACT"
            15, 16 -> "METRICS"
            17, 18, 19 -> "BGET $key"
            20 -> "READS"
            else -> "RANGE $key ${keys[random.nextInt(keys.size)]} ${listOf("0", "1", "2", "1000", "0002")[random.nextInt(5)]}"
        }
        commands += if (random.nextInt(10) == 0) " \t" + line.replace(" ", "\t  ") + "\t " else line
    }
    commands += if (stage == 1) "LAYOUT" else if (stage == 2) "METRICS" else "READS"
    commands.forEachIndexed { index, command ->
        if (index % 97 == 0) emit(" \t ")
        emit(command)
    }
}

fun scaleInput() {
    val capacity = number("--capacity", 1024, 1..4096)
    val writes = number("--writes", if (stage == 3) 90000 else 100000, 1..180000)
    val gets = number("--gets", if (stage == 1) 90000 else if (stage == 2) 50000 else 20000, 0..180000)
    val compacts = if (stage == 1) 0 else number("--compacts", if (stage == 2) 10000 else 1000, 1..100000)
    val ranges = if (stage == 3) number("--ranges", 80000, 0..180000) else 0
    val keyCount = number("--keys", if (stage == 1) writes else 60000, 16..180000)
    val total = 1L + writes + gets + compacts + ranges + if (stage >= 2) 1 else 0
    require(total <= 200000) { "Too many commands" }
    require(stage != 1 || keyCount >= writes) { "Stage 1 scale writes must have distinct keys" }
    emit("RESET $capacity")
    if (stage == 1) {
        repeat(writes) { index -> emit("PUT ${wideKey(index * 2)} V${index.toString(36)}") }
        repeat(gets) { index ->
            val number = when (index % 3) {
                0 -> random.nextInt(minOf(capacity, writes)) * 2
                1 -> (writes - 1 - random.nextInt(minOf(capacity, writes))) * 2
                else -> random.nextInt(writes) * 2 + 1
            }
            emit("GET ${wideKey(number)}")
        }
        return
    }
    repeat(compacts) { cycle ->
        for (index in cycle * writes / compacts until (cycle + 1) * writes / compacts) {
            val chosen = if (index > capacity * 2 && index % 4 == 0) random.nextInt(minOf(index, keyCount)) else index % keyCount
            val key = wideKey(chosen * 2)
            emit(if (index % 7 == 0) "DEL $key" else "PUT $key V${index.toString(36)}")
        }
        emit("COMPACT")
        val visible = minOf(keyCount, maxOf(1, (cycle + 1) * writes / compacts))
        for (index in cycle * gets / compacts until (cycle + 1) * gets / compacts) {
            val key = wideKey(random.nextInt(visible) * 2 + if (index % 3 == 0) 1 else 0)
            emit("${if (stage == 3) "BGET" else "GET"} $key")
        }
        for (index in cycle * ranges / compacts until (cycle + 1) * ranges / compacts) {
            val keyIndex = random.nextInt(visible) * 2
            val lower = wideKey(keyIndex)
            val upper = wideKey(minOf(keyIndex + 8, keyCount * 2))
            emit(when (index % 6) {
                0 -> "RANGE $lower $upper 0"
                1 -> "RANGE z a 2"
                2 -> "RANGE ${wideKey(keyIndex + 1)} ${wideKey(keyIndex + 1)} 1"
                3 -> "RANGE $lower $lower 1"
                else -> "RANGE $lower $upper 2"
            })
        }
    }
    emit(if (stage == 2) "METRICS" else "READS")
}

if (mode == "mixed") mixedInput() else scaleInput()
output.flush()
