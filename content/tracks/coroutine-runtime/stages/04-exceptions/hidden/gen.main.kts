import kotlin.random.Random

data class Options(val roots: Int, val mode: String, val seed: Long)

fun options(arguments: Array<String>): Options {
    require(arguments.size % 2 == 0)
    val values = mutableMapOf("--roots" to "15", "--mode" to "mixed", "--seed" to "1")
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
val codes = listOf("BOOM", "REMOTE_42", "QUARTZ", "X_SIGNAL")
val code = codes[random.nextInt(codes.size)]
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

emit("TASK broken LAUNCH")
emit("ON_COMPLETE report")
emit("FAIL $code")
emit("FINALLY")
emit("LOG cleanup")
emit("END")
emit("TASK late LAUNCH")
emit("DELAY 1")
emit("LOG okay")
emit("END")
emit("TASK bad_async ASYNC")
emit("FAIL ASYNC_ERROR")
emit("RESULT never")
emit("END")
emit("TASK group LAUNCH")
emit("SPAWN broken a")
emit("SPAWN late b")
emit("JOIN b")
emit("END")
emit("TASK async_group LAUNCH")
emit("SPAWN bad_async a")
emit("AWAIT_CATCH a")
emit("SPAWN late b")
emit("JOIN b")
emit("END")
emit("TASK super_parent LAUNCH")
emit("SUPERVISOR group s")
emit("LOG after")
emit("END")
emit("TASK super_async_parent LAUNCH")
emit("SUPERVISOR async_group s")
emit("END")
emit("TASK plain LAUNCH")
emit("SPAWN broken a")
emit("SPAWN late b")
emit("JOIN b")
emit("END")
emit("TASK self LAUNCH")
emit("CANCEL_SELF")
emit("SWALLOW_CANCEL")
emit("LOG after")
emit("DELAY 0")
emit("FINALLY")
emit("LOG cleanup")
emit("END")
emit("TASK double LAUNCH")
emit("FAIL FIRST")
emit("FINALLY")
emit("FAIL SECOND")
emit("END")

if (setting.mode == "errors") {
    when (random.nextInt(6)) {
        0 -> { emit("TASK bad LAUNCH"); emit("FAIL lowercase"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        1 -> { emit("TASK bad LAUNCH"); emit("SUPERVISOR bad_async x"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        2 -> { emit("TASK bad LAUNCH"); emit("SPAWN late x"); emit("AWAIT_CATCH x"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        3 -> { emit("TASK bad LAUNCH"); emit("SPAWN late x"); emit("SUPERVISOR group x"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        4 -> { emit("TASK bad LAUNCH"); emit("SUPERVISOR bad x"); emit("END"); emit("ROOT bad r"); emit("RUN") }
        else -> { emit("TASK bad LAUNCH"); emit("CANCEL_SELF extra"); emit("END"); emit("ROOT bad r"); emit("RUN") }
    }
} else {
    val choices = listOf("super_parent", "super_async_parent", "plain", "self", "double")
    repeat(setting.roots) { index -> emit("ROOT ${choices[index % choices.size]} r$index") }
    emit("RUN")
}
print(output)
