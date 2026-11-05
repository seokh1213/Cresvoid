import kotlin.random.Random

data class Options(val clients: Int, val tries: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--clients" to "8", "--tries" to "600", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        values.getValue("--clients").toInt().also { require(it in 1..1000) },
        values.getValue("--tries").toInt().also { require(it in 0..499_900) },
        values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.tries >= 100_000
val output = System.out.bufferedWriter()
val maxTime = 1_000_000_000_000L
var now = 0L

fun emit(line: String) {
    output.append(line).append('\n')
}

fun <T> pick(values: List<T>): T = values[random.nextInt(values.size)]

fun at(time: Long): Long {
    now = maxOf(now, time)
    return time
}

fun padded(line: String): String =
    if (random.nextInt(25) == 0) "  " + line.replace(" ", "   ") + "  " else line

fun serverDefinitions() {
    listOf(
        "TOKEN c 2 3", "LEAKY q 2 100", "WINDOW w 3 1000",
        "GATEWAY c 0 5 0", "GATEWAY far 10 5 100",
        "RW rw 2 1000", "ATOMIC atomic 2 1000", "LOCAL local 2 1000 0",
    ).forEach(::emit)
}

fun clientName(index: Int): String = when {
    index == 0 -> "c"
    largeScale -> "c$index"
    index == 1 -> "."
    index == 2 -> ":"
    index == 3 -> "Z".repeat(64)
    index == 4 -> "user:42"
    index == 5 -> "10.0.0.1"
    index == 6 -> "API_key-0"
    else -> "caller.$index"
}

val clientNames = List(options.clients, ::clientName)
val windows = List(options.clients) { index ->
    if (largeScale && index == 0) 1_000_000_000L else listOf(1000L, 1L, 999L, 1001L, 1_000_000_000L)[index % 5]
}

fun clientDefinitions() {
    clientNames.forEachIndexed { index, name ->
        val k = listOf("1.000", "1.001", "1.100", "2.000", "99.999", "100.000")[index % 6]
        val seed = when (index % 4) {
            0 -> 0L
            1 -> 4_294_967_295L
            2 -> 1L
            else -> random.nextLong(4_294_967_296L)
        }
        emit("CLIENT $name ${windows[index]} $k $seed")
    }
}

fun fixedProbes() {
    emit("CSTAT c 0")
    emit("TRY c 0 503 2")
    emit("TRY c 500 200 -")
    emit("TRY c 1000 200 -")
    emit("CSTAT c 1500")
    emit("TRY c 1999 429 1000000")
    emit("TRY c 2000 200 -")
    emit("TRY c 2000 200 -")
    emit("CSTAT c 2000")
    emit("REQ c k 2000")
    emit("CALL atomic k 2000 far critical")
    emit("CLIENT late 1000 100.000 2003239710")
    emit("TRY missing 9000 200 -")
    emit("TRY c 9000 200 1")
    emit("CSTAT c 2000")
    emit("CSTAT c 2020")
    emit("TRY late 5 429 0")
    emit("TRY late 2020 429 -")
    emit("CSTAT late 2020")
    now = 2020
}

val badIntegers = listOf("01", "00", "-1", "+1", "1.0", "1e3", "١", "999999999999999999999999")
val badNames = listOf("a/b", "한글", "é", "x".repeat(65), "#")

fun malformedLines(): List<String> = buildList {
    addAll(listOf(
        "client c 1 2.000 0", "TRY", "CSTAT", "CLIENT", "CSTAT c 0 extra",
        "CLIENT c 1000 2.000 0 extra", "TRY c 0 200 - extra", "TRY c 0 201 -",
        "TRY c 0 200 0", "TRY c 0 200 1", "TRY c 0 429 1000001",
        "TRY missing 9000 200 0", "TRY missing 9000 503 -", "CSTAT missing 9000",
        "CLIENT c 1000 2.000 0", "CLIENT c 0 2.000 0",
        "CLIENT unused 1000000001 2.000 0", "CLIENT unused 1 2.000 4294967296",
        "CSTAT c 1000000000001", "TRY c 1000000000001 200 -",
        "TRY c\t0 200 -", "CSTAT c 0\r",
        "REQ missing k 9000", "CALL c k 9000 c get", "PEEK c k 9000",
        "REQ atomic k 9000", "REPORT atomic", "CALL atomic k 9000 missing get",
        "TOKEN c 1 1", "GATEWAY c 0 1 0", "DOWN", "UP 01", "REPORT c",
    ))
    for (word in listOf("1", "1.1", "01.100", "0.999", "100.001", "+2.000", "2.0000", "1e1")) {
        add("CLIENT c 1000 $word 0")
    }
    for (word in badIntegers) {
        add("CLIENT c $word 2.000 0")
        add("CLIENT c 1000 2.000 $word")
        add("TRY c $word 503 -")
        add("TRY c 9000 503 $word")
        add("CSTAT missing $word")
    }
    for (name in badNames) {
        add("CLIENT $name 1 2.000 0")
        add("TRY $name 9000 503 -")
        add("CSTAT $name 9000")
    }
}

val malformed = malformedLines()

fun existingCommands() {
    val key = pick(listOf("k", "alice", "Alice", "_", "user:42", "z".repeat(64)))
    val priority = pick(listOf("critical", "post", "get", "test"))
    when (random.nextInt(10)) {
        0 -> emit("REQ c $key $now")
        1 -> emit("REQ q $key $now")
        2 -> emit("REQ w $key $now")
        3 -> emit("REPORT w")
        4 -> emit("CALL rw $key $now ${pick(listOf("c", "far"))} $priority")
        5 -> emit("CALL atomic $key $now ${pick(listOf("c", "far"))} $priority")
        6 -> emit("CALL local $key $now far $priority")
        7 -> emit("PEEK ${pick(listOf("rw", "atomic", "local"))} $key $now")
        8 -> emit("DOWN $now")
        else -> emit("UP $now")
    }
}

fun nextTime(index: Int): Long {
    if (largeScale) {
        if (index % 97 == 0) return (now - 1).coerceAtLeast(0)
        if (index < options.tries * 9 / 10) return at(now + if (index % 100 == 0) 1 else 0)
        return at(now + if (index % 1000 == 0) 100_000_000L else 1L)
    }
    return when (random.nextInt(12)) {
        0 -> (now - random.nextLong(1, minOf(now, 2000) + 1)).coerceAtLeast(0)
        1 -> at(now + pick(windows) - 1)
        2 -> at(now + pick(windows))
        3 -> at(now + pick(windows) + 1)
        in 4..7 -> now
        else -> at(now + random.nextLong(1, 1001))
    }
}

fun randomTry(index: Int) {
    val name = if (largeScale && random.nextInt(10) != 0) "c" else pick(clientNames)
    val time = nextTime(index)
    val response = when {
        index % 160 < 40 -> "200"
        index % 160 < 100 -> pick(listOf("429", "503"))
        else -> pick(listOf("200", "200", "503"))
    }
    val header = if (response == "200" || random.nextInt(5) < 3) "-"
        else pick(listOf("0", "1", "2", "1000", "1000000"))
    emit(padded("TRY $name $time $response $header"))
}

fun finalProbes() {
    emit("CSTAT c ${at(minOf(maxTime, now + 1_000_000_000L))}")
    emit("TRY c 0 200 -")
    emit("TRY late ${at(maxTime)} 503 1000000")
    emit("TRY late $maxTime 200 -")
    emit("CSTAT late $maxTime")
    emit("CLIENT maximum 1 1.001 634785765")
    emit("TRY maximum 0 429 0")
    emit("CSTAT maximum $maxTime")
}

serverDefinitions()
clientDefinitions()
fixedProbes()
malformed.forEach(::emit)
repeat(options.tries) { index ->
    if (largeScale) {
        if (index % 1000 == 0) emit("CSTAT c $now")
        if (index % 10000 == 0) existingCommands()
        if (index % 7000 == 0) emit(pick(malformed))
    } else {
        if (index % 7 == 0) emit("CSTAT ${pick(clientNames)} $now")
        if (index % 11 == 0) existingCommands()
        if (index % 13 == 0) emit(pick(malformed))
        if (index % 47 == 0) emit(pick(listOf("", "   ")))
    }
    randomTry(index)
}
finalProbes()
output.flush()
