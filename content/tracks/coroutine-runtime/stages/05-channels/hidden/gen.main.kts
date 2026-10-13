import kotlin.random.Random

data class Options(val roots: Int, val mode: String, val seed: Long)

fun options(arguments: Array<String>): Options {
    require(arguments.size % 2 == 0)
    val values = mutableMapOf("--roots" to "10", "--mode" to "mixed", "--seed" to "1")
    arguments.toList().chunked(2).forEach { (key, value) ->
        require(key in values) { "Unknown option: $key" }
        values[key] = value
    }
    val roots = values.getValue("--roots").toInt()
    require(roots in 1..10_000)
    val mode = values.getValue("--mode")
    require(mode in setOf("mixed", "deadlock", "errors"))
    return Options(roots, mode, values.getValue("--seed").toLong())
}

val setting = options(args)
val random = Random(setting.seed)
val words = listOf("red", "blue", "quartz", "moon", "xylophone", "signal")
val errorCase = if (setting.mode == "errors") random.nextInt(7) else -1
fun word(): String = words[random.nextInt(words.size)]
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

emit("TASK sender LAUNCH")
emit("SEND ch ${word()}")
emit("LOG sent")
emit("END")
emit("TASK receiver LAUNCH")
emit("RECV ch")
emit("LOG read")
emit("END")
emit("TASK worker LAUNCH")
emit("LOCK m")
emit("LOG held")
emit("DELAY 0")
emit("UNLOCK m")
emit("END")
emit("TASK parent LAUNCH")
emit("SPAWN sender s")
emit("SPAWN receiver r")
emit("SPAWN worker a")
emit("SPAWN worker b")
emit("JOIN s")
emit("JOIN r")
emit("JOIN a")
emit("JOIN b")
emit("END")
emit("TASK store LAUNCH")
emit("SEND box ${word()}")
emit("SEND box ${word()}")
emit("SEND box ${word()}")
emit("CLOSE box")
emit("END")
emit("TASK drain LAUNCH")
emit("DELAY 0")
emit("RECV box")
emit("RECV box")
emit("RECV box")
emit("END")
if (errorCase == 4 || errorCase == 5) {
    emit("TASK late LAUNCH")
    emit(if (errorCase == 4) "SEND unknown x" else "LOCK unknown")
    emit("END")
}
emit("CHANNEL ch 0")
emit("CHANNEL box 2")
emit("MUTEX m")

when (setting.mode) {
    "mixed" -> {
        repeat(setting.roots) { index -> emit("ROOT parent r$index") }
        emit("ROOT store store")
        emit("ROOT drain drain")
        emit("RUN")
    }
    "deadlock" -> { emit("ROOT sender blocked"); emit("RUN") }
    else -> when (errorCase) {
        0 -> { emit("CHANNEL ch 1"); emit("ROOT parent r"); emit("RUN") }
        1 -> { emit("MUTEX m"); emit("ROOT parent r"); emit("RUN") }
        2 -> { emit("ROOT parent r"); emit("CHANNEL late 1"); emit("RUN") }
        3 -> { emit("CHANNEL bad 10001"); emit("ROOT parent r"); emit("RUN") }
        4, 5 -> { emit("ROOT late r"); emit("RUN") }
        else -> { emit("ROOT missing r"); emit("RUN") }
    }
}
print(output)
