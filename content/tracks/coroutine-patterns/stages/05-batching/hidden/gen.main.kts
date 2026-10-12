import kotlin.random.Random

data class Options(val runs: Int, val changes: Int, val seed: Long)

fun options(arguments: Array<String>): Options {
    val values = mutableMapOf("--runs" to "8", "--changes" to "24", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍이다" }
    for (pair in arguments.toList().chunked(2)) {
        require(pair[0] in values) { "모르는 옵션: ${pair[0]}" }
        values[pair[0]] = pair[1]
    }
    return Options(
        runs = values.getValue("--runs").toInt().also { require(it in 1..100) },
        changes = values.getValue("--changes").toInt().also { require(it in 0..10_000) },
        seed = values.getValue("--seed").toLong(),
    )
}

val setting = options(args)
val random = Random(setting.seed)
val output = StringBuilder()

fun emit(line: String) {
    output.append(line).append('\n')
}

fun countFor(run: Int): Int = when {
    setting.changes == 0 -> 0
    setting.changes >= 1_000 -> setting.changes + random.nextInt(-setting.changes / 10, setting.changes / 10 + 1)
    run % 7 == 0 -> random.nextInt(1, minOf(setting.changes, 5) + 1)
    else -> random.nextInt(maxOf(1, setting.changes / 2), setting.changes + 1)
}

fun timings(count: Int, mode: Int): List<Long> {
    var time = random.nextLong(1, 5)
    return List(count) { index ->
        if (index > 0) time += when (mode) {
            0 -> if (index % 2 == 1) 5 else random.nextLong(1, 4)
            1, 4 -> 1
            else -> random.nextLong(1, 8)
        }
        time
    }
}

repeat(setting.runs) { run ->
    val mode = run % 7
    val count = countFor(run)
    val times = timings(count, mode)
    val last = times.lastOrNull() ?: 0L
    times.forEachIndexed { index, time -> emit("CHANGE $time r${run}c$index") }
    val sourceTime = last + if (mode == 3) 7 else random.nextLong(1, 9)
    val sourceKind = if (mode == 3) "FAIL" else "END"
    emit("SOURCE $sourceTime $sourceKind")

    val size = when (mode) {
        0 -> 2
        1, 4, 5 -> 1
        2 -> count + 1
        3 -> if (count >= 2) 2 else 1
        else -> random.nextInt(2, 9)
    }
    val window = when (mode) {
        0 -> 5L
        2, 3 -> last + 100L
        else -> random.nextLong(2, 21)
    }
    val waiting = when (mode) {
        1 -> 0
        3 -> count + 1
        else -> random.nextInt(0, 4)
    }
    val saveDelay = when (mode) {
        1, 4 -> 30L
        3 -> last + 100L
        else -> random.nextLong(1, 14)
    }
    val failOn = if (mode == 5 && count > 0) random.nextInt(1, minOf(count, 3) + 1) else 0
    val cancelAt = if (mode == 4 && count > 0) times.first() + random.nextLong(2, 15) else 0L
    if (setting.changes >= 1_000 && run == 0) emit("TRACE OFF")
    emit("PERSIST $size $window $waiting $saveDelay $failOn $cancelAt")
}

print(output.toString())
