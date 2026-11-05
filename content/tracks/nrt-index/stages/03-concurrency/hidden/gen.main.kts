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

val durabilityErrors = listOf(
    "DURABILITY", "DURABILITY REQUEST", "DURABILITY other", "DURABILITY async extra",
    "SYNC extra", "FLUSH extra", "CRASH extra", "LOG extra",
)

fun durabilityPrelude() {
    emit("LOG")
    emit("DURABILITY async")
    write("PUT ${id(0)} old")
    emit("DURABILITY request")
    emit("LOG")
    emit("CRASH")
    emit("GET ${id(0)}")
    emit("DURABILITY async")
    write("PUT ${id(0)} red")
    emit("DURABILITY request")
    write("PUT ${id(1)} blue")
    emit("LOG")
    emit("CRASH")
    emit("STATE")
    emit("SEGMENTS")
    emit("SEARCH *")
    emit("FLUSH")
    emit("DURABILITY async")
    write("DEL ${id(0)}")
    emit("REFRESH")
    emit("MERGE")
    emit("SEARCH *")
    emit("CRASH")
    emit("GET ${id(0)}")
    write("PUT ${id(0)} green")
    emit("SYNC")
    write("DEL ${id(0)}")
    emit("CRASH")
    emit("CRASH")
    emit("LOG")
    emit("GET ${id(0)}")
    emit("SEARCH *")
    emit("FLUSH")
    emit("FLUSH")
    emit("DURABILITY async")
    write("PUT ${id(1)} quasar")
    emit("TICK 4999")
    emit("LOG")
    emit("TICK 1")
    emit("LOG")
    emit("TICK 5000")
    emit("LOG")
    emit("CRASH")
    emit("STATE")
    emit("SYNC")
    emit("DURABILITY request")
}

fun durabilityRandom() {
    when (random.nextInt(10)) {
        in 0..3 -> emit("DURABILITY ${if (random.nextBoolean()) "request" else "async"}")
        4 -> emit("SYNC")
        5 -> emit("FLUSH")
        6, 7 -> emit("CRASH")
        8 -> emit("LOG")
        else -> emit(durabilityErrors.random(random))
    }
}

val concurrencyErrors = listOf(
    "VERSION", "VERSION a extra", "RGET A", "RSTATE extra", "PROMOTE extra",
    "CAS a 0 0 red", "CAS a -1 1 red", "CAS a 01 1 red", "CAS a 0 +1 red",
    "CAS a 1000000001 1 red", "CAS a 0 1000000001 red", "CAS a 0 1", "CAS a 0 1 RED",
    "REPLAY 0 0 DEL a", "REPLAY 00 1 DEL a", "REPLAY 0 1 GET a", "REPLAY 0 1 PUT a",
    "REPLAY 0 1 DEL a extra", "REPLAY 0 1000000001 DEL a", "REPLAY 1000000001 1 DEL a",
    "REPLAY 999999999999999999999 1 DEL a", "REPLAY 0 1 DEL A",
)
val packets = mutableListOf<String>()
var nextReplay = 10L

fun deliver(packet: String) {
    packets.add(packet)
    emit(packet)
}

fun successfulCas() {
    val target = id()
    val expected = primarySequence
    val term = currentTerm
    write("PUT $target ${body()}")
    if (random.nextBoolean()) emit("PROMOTE")
    if (emit("CAS $target $expected $term ${body()}")) primarySequence++
    emit("CAS $target $expected $term red")
    emit("VERSION $target")
}

fun concurrencyPrelude() {
    emit("VERSION ${id(0)}")
    emit("RGET ${id(0)}")
    emit("RSTATE")
    successfulCas()
    write("DEL ${id(0)}")
    emit("VERSION ${id(0)}")
    emit("CAS ${id(0)} ${primarySequence - 1} $currentTerm red")
    emit("REFRESH")
    emit("MERGE")
    emit("VERSION ${id(0)}")
    val term = currentTerm
    deliver("REPLAY 2 $term DEL ${id(0)}")
    deliver("REPLAY 0 $term PUT ${id(0)} older")
    emit("RSTATE")
    deliver("REPLAY 1 $term PUT ${id(1)} newest")
    emit(packets.last())
    emit("RSTATE")
    emit("RGET ${id(0)}")
    emit("PROMOTE")
    emit(packets.first())
    deliver("REPLAY 3 $term DEL ${id(1)}")
    deliver("REPLAY 4 $currentTerm PUT ${id(0)} green")
    deliver("REPLAY 5 ${currentTerm + 1} DEL ${id(0)}")
    deliver("REPLAY 1000000000 $currentTerm DEL ${id(1)}")
    deliver("REPLAY 6 1000000000 PUT ${id(0)} future")
    emit("CAS ${id(0)} 1000000000 1000000000 red")
    emit("RSTATE")
    emit("CRASH")
    emit("RSTATE")
    emit("RGET ${id(0)}")
    emit("DURABILITY async")
    val savedSequence = primarySequence
    val savedTerm = currentTerm
    write("PUT ${id(0)} saved")
    emit("SYNC")
    write("PUT ${id(0)} lost")
    emit("PROMOTE")
    emit("CRASH")
    emit("VERSION ${id(0)}")
    if (emit("CAS ${id(0)} $savedSequence $savedTerm recovered")) primarySequence++
    emit("VERSION ${id(0)}")
    emit("FLUSH")
    emit("CRASH")
    emit("VERSION ${id(0)}")
}

fun concurrencyRandom() {
    when (random.nextInt(10)) {
        0 -> successfulCas()
        1 -> emit("CAS ${id()} 1000000000 1000000000 ${body()}")
        2 -> emit("VERSION ${id()}")
        3 -> emit("PROMOTE")
        4, 5 -> {
            val serial = nextReplay++
            val sequence = if (random.nextInt(4) == 0) 1_000_000_000L - serial else serial
            val term = when (random.nextInt(5)) {
                0 -> maxOf(1, currentTerm - 1)
                1 -> currentTerm + 1
                else -> currentTerm
            }
            val mutation = if (random.nextBoolean()) "PUT ${id()} ${body()}" else "DEL ${id()}"
            deliver("REPLAY $sequence $term $mutation")
        }
        6 -> if (packets.isNotEmpty()) emit(packets.random(random)) else emit("RSTATE")
        7 -> emit("RGET ${id()}")
        8 -> emit("RSTATE")
        else -> emit(concurrencyErrors.random(random))
    }
}

concurrencyPrelude()
durabilityPrelude()
basePrelude()
(baseErrors + durabilityErrors + concurrencyErrors).forEach(::emit)
while (lines.size < commandLimit) {
    when (random.nextInt(4)) {
        0 -> concurrencyRandom()
        1 -> durabilityRandom()
        else -> baseRandom()
    }
}
val output = System.out.bufferedWriter()
for ((index, line) in lines.withIndex()) {
    if (index % 37 == 0) output.appendLine(" \t")
    output.appendLine(if (index % 11 == 0) " \t${line.replace(" ", "\t  ")}  " else line)
}
output.flush()
