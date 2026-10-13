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
val words = listOf("ready", "trace", "ember", "quartz", "moon", "xylophone", "yes", "signal")
fun word(): String = words[random.nextInt(words.size)]
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

emit("TASK leaf LAUNCH")
emit("CONTEXT tag")
emit("CONTEXT trace")
emit("DELAY ${random.nextInt(0, 3)}")
emit("END")
emit("TASK answer ASYNC")
emit("DELAY 0")
emit("RESULT ${word()}")
emit("END")
emit("TASK branch LAUNCH")
emit("SPAWN leaf g trace=grand")
emit("END")
emit("TASK parent LAUNCH")
emit("CONTEXT tag")
emit("SPAWN leaf a LAZY tag=child")
emit("STATE a")
emit("START a")
emit("STATE a")
emit("JOIN a")
emit("STATE a")
emit("SPAWN answer b LAZY trace=branch")
emit("AWAIT b")
emit("SCOPE branch s tag=scope")
emit("CONTEXT trace")
emit("END")

if (setting.mode == "errors") {
    when (random.nextInt(7)) {
        0 -> { emit("TASK dangling LAUNCH"); emit("SPAWN leaf x LAZY"); emit("END"); emit("ROOT dangling r"); emit("RUN") }
        1 -> { emit("TASK dangling LAUNCH"); emit("STATE missing"); emit("END"); emit("ROOT dangling r"); emit("RUN") }
        2 -> { emit("TASK dangling LAUNCH"); emit("SCOPE answer x"); emit("END"); emit("ROOT dangling r"); emit("RUN") }
        3 -> { emit("TASK dangling LAUNCH"); emit("SPAWN leaf x tag=one LAZY"); emit("END"); emit("ROOT dangling r"); emit("RUN") }
        4 -> { emit("TASK dangling LAUNCH"); emit("SPAWN leaf x"); emit("SCOPE leaf x"); emit("END"); emit("ROOT dangling r"); emit("RUN") }
        5 -> { emit("TASK dangling LAUNCH"); emit("SCOPE dangling x"); emit("END"); emit("ROOT dangling r"); emit("RUN") }
        else -> { emit("ROOT parent r trace=one tag=two"); emit("RUN") }
    }
} else {
    repeat(setting.roots) { index -> emit("ROOT parent r$index tag=${word()} trace=${word()}") }
    emit("RUN")
}
print(output)
