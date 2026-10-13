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
val words = listOf("ready", "cleanup", "quartz", "moon", "xylophone", "signal")
fun word(): String = words[random.nextInt(words.size)]
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

emit("TASK leaf LAUNCH")
emit("ON_COMPLETE ${word()}")
emit("LOG ${word()}")
emit("DELAY 3")
emit("LOG late")
emit("FINALLY")
emit("LOG ${word()}")
emit("SHIELD_DELAY 1")
emit("LOG cleaned")
emit("END")
emit("TASK fast LAUNCH")
emit("DELAY 0")
emit("FINALLY")
emit("DELAY 0")
emit("END")
emit("TASK parent LAUNCH")
emit("ON_COMPLETE parent")
emit("SPAWN leaf a LAZY")
emit("STATE a")
emit("CANCEL a")
emit("JOIN a")
emit("SPAWN leaf b")
emit("DELAY 0")
emit("CANCEL b")
emit("JOIN b")
emit("TIMEOUT 1 leaf c")
emit("LOG ${word()}")
emit("END")
emit("TASK yielding LAUNCH")
emit("SPAWN fast f")
emit("YIELD")
emit("JOIN f")
emit("ENSURE")
emit("END")

if (setting.mode == "errors") {
    when (random.nextInt(7)) {
        0 -> { emit("TASK bad LAUNCH"); emit("SHIELD_DELAY 1"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        1 -> { emit("TASK bad LAUNCH"); emit("FINALLY"); emit("YIELD"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        2 -> { emit("TASK bad LAUNCH"); emit("FINALLY"); emit("FINALLY"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        3 -> { emit("TASK bad LAUNCH"); emit("TIMEOUT 0 leaf c"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        4 -> { emit("TASK bad LAUNCH"); emit("CANCEL unknown"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        5 -> { emit("TASK bad LAUNCH"); emit("SPAWN leaf c LAZY"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        else -> { emit("TASK bad LAUNCH"); emit("TIMEOUT 1 bad c"); emit("END"); emit("ROOT bad r"); emit("RUN") }
    }
} else {
    repeat(setting.roots) { index ->
        val definition = if (index % 7 == 0) "yielding" else "parent"
        emit("ROOT $definition r$index")
    }
    emit("RUN")
}
print(output)
