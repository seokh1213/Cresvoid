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
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

if (setting.mode == "mixed") {
    val first = random.nextInt(-1_000_000_000, 1_000_000_001)
    val second = random.nextInt(-1_000_000_000, 1_000_000_001)
    val marker = listOf("moon", "quartz", "signal", "crescent")[random.nextInt(4)]
    emit("TASK worker LAUNCH")
    emit("ITER values a")
    emit("ITER values b")
    emit("HAS_NEXT a")
    emit("NEXT b")
    emit("NEXT a")
    emit("HAS_NEXT b")
    emit("END")
    emit("FLOW values")
    emit("EMIT 9")
    emit("END")
    emit("SEQUENCE values")
    emit("LOG $marker")
    emit("YIELD $first")
    emit("YIELD $second")
    emit("END")
    repeat(setting.roots) { index -> emit("ROOT worker r$index") }
    emit("RUN")
} else {
    when (random.nextInt(10)) {
        0 -> {
            emit("TASK worker LAUNCH")
            emit("ITER values a")
            emit("END")
            emit("SEQUENCE values")
            emit("END")
            emit("SEQUENCE values")
            emit("END")
        }
        1 -> {
            emit("TASK worker LAUNCH")
            emit("ITER values a")
            emit("ITER absent a")
            emit("END")
            emit("SEQUENCE values")
            emit("END")
        }
        2 -> {
            emit("TASK worker LAUNCH")
            emit("ITER absent a")
            emit("END")
        }
        3 -> {
            emit("TASK worker LAUNCH")
            emit("HAS_NEXT a")
            emit("ITER values a")
            emit("END")
            emit("SEQUENCE values")
            emit("END")
        }
        4 -> {
            emit("TASK worker LAUNCH")
            emit("ITER values a")
            emit("END")
            emit("SEQUENCE values")
            emit("YIELD -0")
            emit("END")
        }
        5 -> {
            emit("TASK worker LAUNCH")
            emit("LOG ok")
            emit("END")
            emit("CHANNEL ch 0")
            emit("SEQUENCE values")
            emit("END")
        }
        6 -> {
            emit("TASK worker LAUNCH")
            emit("ITER values a")
            emit("END")
            emit("SEQUENCE values")
            emit("FINALLY")
            emit("END")
        }
        7 -> {
            emit("TASK worker LAUNCH")
            emit("YIELD 1")
            emit("END")
        }
        8 -> {
            emit("TASK worker LAUNCH")
            emit("LOG ok")
            emit("END")
            emit("FLOW flow")
            emit("NEXT a")
            emit("END")
        }
        else -> {
            emit("TASK worker LAUNCH")
            emit("ITER values a")
            emit("END")
            emit("SEQUENCE values")
            emit("YIELD")
            emit("END")
        }
    }
    emit("ROOT worker r")
    emit("RUN")
}

print(output)
