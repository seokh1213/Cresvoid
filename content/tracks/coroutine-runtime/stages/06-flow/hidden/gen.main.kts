import kotlin.random.Random

data class Options(val roots: Int, val mode: String, val seed: Long)

fun options(arguments: Array<String>): Options {
    require(arguments.size % 2 == 0)
    val values = mutableMapOf("--roots" to "12", "--mode" to "mixed", "--seed" to "1")
    arguments.toList().chunked(2).forEach { (key, value) ->
        require(key in values) { "Unknown option: $key" }
        values[key] = value
    }
    val roots = values.getValue("--roots").toInt()
    require(roots in 1..10_000)
    val mode = values.getValue("--mode")
    require(mode in setOf("mixed", "errors"))
    return Options(roots, mode, values.getValue("--seed").toLong())
}

val setting = options(args)
val random = Random(setting.seed)
val words = listOf("open", "close", "quartz", "moon", "xylophone", "signal")
fun word(): String = words[random.nextInt(words.size)]
val errorCase = if (setting.mode == "errors") random.nextInt(7) else -1
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

emit("TASK worker LAUNCH")
emit("COLLECT seq MAP 1 FILTER EVEN TAKE 1")
emit("COLLECT seq FILTER ODD TAKE 2")
emit("LOG after")
emit("END")
emit("TASK failing LAUNCH")
emit("COLLECT boom")
emit("LOG unreachable")
emit("FINALLY")
emit("LOG taskclean")
emit("END")
emit("TASK collector LAUNCH")
emit("COLLECT slow")
emit("LOG late")
emit("FINALLY")
emit("LOG taskclean")
emit("END")
emit("TASK parent LAUNCH")
emit("SPAWN collector c")
emit("DELAY 0")
emit("CANCEL c")
emit("JOIN c")
emit("END")
if (errorCase == 1 || errorCase == 5 || errorCase == 6) {
    emit("TASK bad LAUNCH")
    emit(when (errorCase) {
        1 -> "COLLECT unknown"
        5 -> "EMIT 1"
        else -> "COLLECT seq FILTER ODD MAP 1"
    })
    emit("END")
}
emit("FLOW seq")
emit("LOG ${word()}")
emit("EMIT -3")
emit("DELAY 0")
emit("EMIT -2")
emit("DELAY 1")
emit("EMIT 1")
emit("FINALLY")
emit("LOG ${word()}")
emit("SHIELD_DELAY 0")
emit("END")
emit("FLOW boom")
emit("EMIT 4")
emit("FAIL OOPS")
emit("FINALLY")
emit("LOG cleanup")
emit("END")
emit("FLOW slow")
emit("LOG ${word()}")
emit("DELAY 5")
emit("EMIT 9")
emit("FINALLY")
emit("SHIELD_DELAY 2")
emit("LOG cleaned")
emit("END")

if (setting.mode == "mixed") {
    repeat(setting.roots) { index -> emit("ROOT worker r$index") }
    emit("ROOT failing failed")
    emit("ROOT parent parent")
    emit("RUN")
} else when (errorCase) {
    0 -> { emit("FLOW seq"); emit("END"); emit("ROOT worker r"); emit("RUN") }
    1, 5, 6 -> { emit("ROOT bad r"); emit("RUN") }
    2 -> { emit("FLOW bad"); emit("EMIT -0"); emit("END"); emit("ROOT worker r"); emit("RUN") }
    3 -> { emit("FLOW bad"); emit("FINALLY"); emit("EMIT 1"); emit("END"); emit("ROOT worker r"); emit("RUN") }
    4 -> { emit("CHANNEL ch 0"); emit("FLOW late"); emit("END"); emit("ROOT worker r"); emit("RUN") }
    else -> error("Unexpected case")
}
print(output)
