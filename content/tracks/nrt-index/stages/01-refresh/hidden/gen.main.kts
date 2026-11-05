import kotlin.random.Random

fun option(name: String, default: Int): Int {
    val position = args.indexOf(name)
    return if (position < 0) default else args.getOrNull(position + 1)?.toIntOrNull()
        ?: error("$name requires an integer")
}

val allowedOptions = setOf("--seed", "--commands", "--docs")
require(args.size % 2 == 0 && args.indices.step(2).all { args[it] in allowedOptions })
val seed = option("--seed", 1)
val commandLimit = option("--commands", 600)
val documentCount = option("--docs", 40)
require(commandLimit in 1..6_000 && documentCount in 1..2_000)
val random = Random(seed)
val ids = List(documentCount) { index ->
    when (index) {
        0 -> "a"
        1 -> "z123456789012345"
        else -> "doc_$index"
    }
}
val vocabulary = listOf("red", "blue", "green", "apple", "index", "quasar", "x", "abcdefghijklmnopqrst")
val lines = mutableListOf<String>()
var searchBudget = 30_000
var segmentBudget = 30_000
var primarySequence = 0L
var currentTerm = 1L

fun id(index: Int = random.nextInt(ids.size)): String = ids[index % ids.size]
fun body(): String = List(random.nextInt(1, 9)) { vocabulary.random(random) }.joinToString(" ")

fun emit(command: String): Boolean {
    if (lines.size >= commandLimit) return false
    val bounded = when {
        command.startsWith("SEARCH ") -> {
            if (searchBudget < documentCount) "STATE" else {
                searchBudget -= documentCount
                command
            }
        }
        command == "SEGMENTS" -> {
            // No execution can create more segments than there are preceding commands.
            val upperBound = lines.size
            if (segmentBudget < upperBound) "STATE" else {
                segmentBudget -= upperBound
                command
            }
        }
        else -> command
    }
    lines.add(bounded)
    if (bounded == "PROMOTE") currentTerm++
    return true
}

fun write(command: String) {
    if (emit(command)) primarySequence++
}

fun basePrelude() {
    emit("STATE")
    emit("SEGMENTS")
    emit("GET ${id(0)}")
    emit("MERGE")
    emit("REFRESH")
    write("DEL ${id(0)}")
    emit("REFRESH")
    write("PUT ${id(0)} red red")
    emit("TICK 1000")
    emit("STATE")
    emit("SEARCH red")
    write("PUT ${id(0)} blue")
    write("PUT ${id(0)} blue")
    write("DEL ${id(1)}")
    emit("SEARCH blue")
    emit("GET ${id(0)}")
    emit("REFRESH")
    emit("SEGMENTS")
    emit("MERGE")
    emit("SEGMENTS")
    write("DEL ${id(0)}")
    emit("MERGE")
    emit("SEARCH *")
    emit("TICK 999")
    emit("STATE")
    emit("TICK 1")
    emit("SEGMENTS")
    emit("MERGE")
    write("PUT ${id(1)} abcdefghijklmnopqrst x x red blue green quasar apple")
    write("DEL ${id(1)}")
    emit("REFRESH")
    emit("SEGMENTS")
    emit("MERGE")
    emit("SEARCH *")
    emit("TICK 29999")
    write("PUT ${id(0)} quasar")
    emit("SEARCH quasar")
    emit("TICK 30000")
    write("PUT ${id(1)} green")
    emit("TICK 0")
    emit("STATE")
    emit("SEARCH green")
    emit("TICK 60000")
    emit("GET ${id(1)}")
    emit("REFRESH")
}

val baseErrors = listOf(
    "UNKNOWN", "put a red", "PUT", "PUT A red", "PUT 1a red", "PUT abcdefghijklmnopq red",
    "PUT a", "PUT a RED", "PUT a abcdefghijklmnopqrstu", "PUT a a a a a a a a a a",
    "DEL", "DEL a extra", "GET", "GET a extra", "SEARCH", "SEARCH **", "SEARCH Red",
    "SEARCH a b", "TICK", "TICK -1", "TICK +1", "TICK 01", "TICK 1.0", "TICK 60001",
    "TICK 999999999999999999999999999999", "TICK 1 extra", "REFRESH extra", "MERGE extra",
    "SEGMENTS extra", "STATE extra", "PUT a red\u000bblue", "SEARCH\u00a0red",
)

fun baseRandom() {
    when (random.nextInt(100)) {
        in 0..36 -> write("PUT ${id()} ${body()}")
        in 37..49 -> write("DEL ${id()}")
        in 50..58 -> emit("GET ${id()}")
        in 59..65 -> emit("SEARCH ${if (random.nextInt(4) == 0) "*" else vocabulary.random(random)}")
        in 66..75 -> emit("TICK ${listOf(0, 1, 999, 1000, 4999, 5000, 29999, 30000, 30001, 60000).random(random)}")
        in 76..82 -> emit("REFRESH")
        in 83..88 -> emit("MERGE")
        in 89..92 -> emit("SEGMENTS")
        in 93..96 -> emit("STATE")
        else -> emit(baseErrors.random(random))
    }
}

basePrelude()
baseErrors.forEach(::emit)
while (lines.size < commandLimit) baseRandom()
val output = System.out.bufferedWriter()
for ((index, line) in lines.withIndex()) {
    if (index % 37 == 0) output.appendLine(" \t")
    output.appendLine(if (index % 11 == 0) " \t${line.replace(" ", "\t  ")}  " else line)
}
output.flush()
