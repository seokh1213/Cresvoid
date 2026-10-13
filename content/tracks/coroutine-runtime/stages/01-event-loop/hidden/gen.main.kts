import kotlin.random.Random

data class Options(val roots: Int, val mode: String, val seed: Long)

fun options(arguments: Array<String>): Options {
    require(arguments.size % 2 == 0)
    val values = mutableMapOf("--roots" to "18", "--mode" to "mixed", "--seed" to "1")
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
val words = listOf("ready", "awake", "ember", "quartz", "moon", "xylophone", "yes", "signal")
fun word(): String = words[random.nextInt(words.size)]
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

emit("TASK leaf LAUNCH")
emit("LOG ${word()}")
emit("DELAY ${random.nextInt(0, 4)}")
emit("LOG ${word()}")
emit("END")
emit("TASK answer ASYNC")
emit("DELAY 0")
emit("RESULT ${word()}")
emit("END")
emit("TASK parent LAUNCH")
emit("LOG ${word()}")
emit("SPAWN leaf c")
emit("SPAWN answer a")
emit("AWAIT a")
emit("DELAY 0")
emit("LOG ${word()}")
emit("END")
emit("TASK empty LAUNCH")
emit("END")
emit("TASK again LAUNCH")
emit("SPAWN answer a")
emit("DELAY 0")
emit("AWAIT a")
emit("AWAIT a")
emit("END")

if (setting.mode == "errors") {
    when (random.nextInt(6)) {
        0 -> emit("ROOT missing r")
        1 -> { emit("ROOT parent r"); emit("ROOT empty r") }
        2 -> { emit("ROOT parent r"); emit("TASK late LAUNCH") }
        3 -> { emit("ROOT answer r") }
        4 -> { emit("ROOT parent r"); emit("RUN extra") }
        else -> { emit("ROOT parent r"); emit("RUN"); emit("LOG extra") }
    }
    if (!output.endsWith("RUN\n") && !output.endsWith("RUN extra\n") && !output.endsWith("LOG extra\n")) emit("RUN")
} else {
    repeat(setting.roots) { index ->
        val definition = when (index % 7) { 0 -> "empty"; 1 -> "again"; else -> "parent" }
        emit("ROOT $definition r$index")
    }
    emit("RUN")
}
print(output)
