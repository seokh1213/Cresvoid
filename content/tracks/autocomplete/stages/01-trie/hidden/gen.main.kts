import kotlin.random.Random

val stage = 1
val scale = "--scale" in args
fun integer(name: String, fallback: Int): Int {
    val position = args.indexOf(name)
    return if (position < 0) fallback else args.getOrNull(position + 1)?.toIntOrNull()
        ?: error("Expected integer after $name")
}
val terms = integer("--terms", if (scale) 2_000_000 else 80)
val commands = integer("--commands", if (scale) 50_000 else 400)
val random = Random(integer("--seed", 1))
require(terms in 1..2_000_000 && commands in 0..50_000)
val output = System.out.bufferedWriter(Charsets.UTF_8)
fun emit(line: String) { output.appendLine(line) }
fun numbered(index: Int): String {
    val letter = if (index < terms * 9L / 10) 'a' else ('b'.code + index % 10).toChar()
    return "$letter${index.toString().padStart(7, '0')}"
}
val ordinary = listOf("app", "apple", "apply", "banana", "가", "가방", "ㄱㅏ", "가", "ᄀ", "힣", "ㆎ", "ᇿ", "0")
val invalid = listOf(
    "BOGUS", "add app 1", "ADD", "ADD app", "ADD app 1 extra", "ADD * 1",
    "ADD A 1", "ADD abc_ 1", "ADD 😀 1", "ADD \u00a0 1", "ADD \u3130 1", "ADD \u318f 1",
    "ADD \u1200 1", "ADD \ud7a4 1", "ADD ${"a".repeat(33)} 1", "ADD app 0",
    "ADD app -1", "ADD app +1", "ADD app 1.0", "ADD app １", "ADD app ١",
    "ADD app 9223372036854775808", "QUERY 0 *", "QUERY 101 *", "QUERY +1 *",
    "QUERY 1 a*", "QUERY 1 A", "QUERY 1", "QUERY 1 * extra", "QUERY 1 ${"a".repeat(33)}",
)

fun cachePrelude() {
    listOf("COST", "REFRESH", "CQUERY 1 *", "CACHE BATCH 2", "ADD apply 100",
        "CQUERY 2 app", "QUERY 2 app", "REFRESH", "COST", "CACHE IMMEDIATE 2",
        "ADD apple 200", "CQUERY 2 app", "ADD overflow 1", "COST",
        "CACHE WRONG 1", "CACHE BATCH 0", "CACHE IMMEDIATE 101", "CACHE BATCH +2",
        "CACHE BATCH 1 extra", "CQUERY 3 app", "REFRESH extra", "COST extra").forEach(::emit)
}
fun filterPrelude() {
    listOf("SHARDS 64", "ROUTE *", "LAYOUT", "SHARDS 2", "BAN unknown", "BAN app",
        "BAN app", "BAN apple", "SQUERY 5 app", "ADD unknown 12", "SQUERY 100 *",
        "UNBAN app", "UNBAN missing", "UNBAN unknown", "ROUTE azzz", "LAYOUT",
        "BAN *", "UNBAN *", "SHARDS 0", "SHARDS 65", "SHARDS -1", "BAN A",
        "ROUTE a*", "LAYOUT extra", "SQUERY 101 *", "UNBAN", "SHARDS", "ROUTE").forEach(::emit)
}
fun normalInput() {
    listOf(" \t ", "QUERY 100 *", "ADD overflow 9223372036854775807", "ADD overflow 1",
        "ADD ${"a".repeat(32)} 0001").forEach(::emit)
    for (term in ordinary) emit("ADD $term 7")
    listOf("QUERY 100 *", "QUERY 1 ᄀ", "QUERY 1 ㄱ", "QUERY 1 가").forEach(::emit)
    val vocabulary = List(terms) {
        if (it < ordinary.size) ordinary[it] else "${if (it % 5 == 0) "힣" else "a"}${it.toString(36)}"
    }
    for (term in vocabulary) emit("ADD $term ${random.nextInt(1, 10)}")
    invalid.forEach(::emit)
    if (stage >= 2) cachePrelude()
    if (stage >= 3) filterPrelude()
    repeat(commands) { turn ->
        val term = vocabulary[random.nextInt(if (turn % 4 == 0) minOf(8, terms) else terms)]
        val prefix = if (turn % 5 == 0) "*" else term.take(random.nextInt(1, term.length + 1))
        val count = listOf(1, 2, 5, 100)[random.nextInt(4)]
        when (random.nextInt(if (stage == 1) 6 else if (stage == 2) 10 else 16)) {
            0, 1, 2 -> emit(" \tADD\t$term\t00${random.nextInt(1, 100)} ")
            3 -> emit(invalid[random.nextInt(invalid.size)])
            4 -> if (stage >= 2) emit("CACHE ${if (turn % 2 == 0) "BATCH" else "IMMEDIATE"} ${listOf(1, 2, 5, 100)[random.nextInt(4)]}")
                else emit("QUERY $count $prefix")
            5 -> if (stage >= 2) emit("CQUERY $count $prefix") else emit("QUERY $count $prefix")
            6 -> emit("REFRESH")
            7 -> emit("COST")
            8 -> if (stage >= 3) emit("BAN $term") else emit("QUERY $count $prefix")
            9 -> if (stage >= 3) emit("UNBAN $term") else emit("QUERY $count $prefix")
            10 -> emit("SHARDS ${listOf(1, 2, 3, 16, 64)[random.nextInt(5)]}")
            11 -> emit("ROUTE $prefix")
            12 -> emit("LAYOUT")
            13 -> emit("SQUERY $count $prefix")
            else -> emit("QUERY $count $prefix")
        }
    }
}
fun scaleInput() {
    for (index in 0 until terms) {
        val frequency = if (index < 10_000) 1_000_000L + index else random.nextLong(1, 30)
        emit("ADD ${numbered(index)} $frequency")
    }
    if (stage == 1) emit("QUERY 1 *")
    if (stage >= 2) { emit("CACHE BATCH 5"); emit("COST") }
    if (stage == 3) {
        emit("SHARDS 16")
        emit("LAYOUT")
        repeat(minOf(10_000, terms)) { emit("BAN ${numbered(it)}") }
    }
    emit("ADD ${numbered(terms - 1)} 9223372036854775808")
    repeat(commands) { turn ->
        if (stage == 2 && turn == commands / 4) emit("REFRESH")
        if (stage == 2 && turn == commands / 2) emit("CACHE IMMEDIATE 5")
        if (stage == 2 && turn == commands * 3 / 4) emit("REFRESH")
        if (stage == 3 && turn == commands / 2) { emit("REFRESH"); emit("SHARDS 16") }
        if (turn % 5 == 0) emit("ADD ${numbered(random.nextInt(terms))} 2000000")
        if (stage == 3 && turn < 10_000 && turn % 2 == 0) {
            emit("UNBAN ${numbered(turn % minOf(10_000, terms))}")
        }
        val prefix = when (stage) {
            1 -> numbered(random.nextInt(terms)).dropLast(1)
            2 -> if (turn % 2 == 0) "*" else numbered(random.nextInt(terms)).take(2)
            else -> if (turn % 2 == 0) "*" else "a"
        }
        val query = if (stage == 1) "QUERY" else if (stage == 2) "CQUERY" else "SQUERY"
        emit("$query 1 $prefix")
    }
    if (stage >= 2) { emit("QUERY 1 *"); emit("COST") }
    if (stage == 3) listOf("ROUTE *", "ROUTE aunknown", "LAYOUT", "CQUERY 1 *").forEach(::emit)
}
if (scale) scaleInput() else normalInput()
output.flush()
