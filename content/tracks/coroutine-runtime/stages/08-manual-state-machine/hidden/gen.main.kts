import kotlin.random.Random

data class Options(val machines: Int, val mode: String, val seed: Long)

fun options(arguments: Array<String>): Options {
    require(arguments.size % 2 == 0)
    val values = mutableMapOf("--mode" to "mixed", "--seed" to "1")
    arguments.toList().chunked(2).forEach { (key, value) ->
        require(key in setOf("--mode", "--seed", "--machines")) { "Unknown option: $key" }
        values[key] = value
    }
    val mode = values.getValue("--mode")
    require(mode in setOf("mixed", "errors", "large"))
    val defaultMachines = if (mode == "large") 10_000 else 12
    val machines = values["--machines"]?.toInt() ?: defaultMachines
    require(machines in 1..10_000)
    return Options(machines, mode, values.getValue("--seed").toLong())
}

val setting = options(args)
val random = Random(setting.seed)
val output = StringBuilder()
fun emit(line: String) { output.append(line).append('\n') }

fun delay(index: Int): Int = when (index % 5) {
    0 -> 0
    1 -> 1
    2 -> 2
    3 -> random.nextInt(3, 101)
    else -> random.nextInt(0, 1_001)
}

fun emitValid() {
    repeat(setting.machines) { index ->
        val first = random.nextInt(0, 1_000_001)
        val multiplier = random.nextInt(0, 1_001)
        val firstDelay = delay(index)
        val secondDelay = delay(index + 2)
        val outcome = if (setting.mode == "large") "OK" else when (index % 3) {
            0 -> "OK"
            1 -> "FAIL_FIRST"
            else -> "FAIL_SECOND"
        }
        emit("MANUAL m$index $first $multiplier $firstDelay $secondDelay $outcome")
    }
    emit("RUN")
}

fun emitError() {
    emit("MANUAL base 3 4 0 0 OK")
    when (random.nextInt(11)) {
        0 -> emit("MANUAL base 7 2 0 1 OK")
        1 -> emit("MANUAL Bad 7 2 0 1 OK")
        2 -> emit("MANUAL other 07 2 0 1 OK")
        3 -> emit("MANUAL other 7 1001 0 1 OK")
        4 -> emit("MANUAL other 7 2 1000000001 1 OK")
        5 -> emit("MANUAL other 7 2 0 1 FAILED")
        6 -> emit("TASK worker LAUNCH")
        7 -> { emit("RUN"); emit("MANUAL other 7 2 0 1 OK") }
        8 -> emit("MANUAL other 7 2 0 1 OK extra")
        9 -> emit("MANUAL\tother 7 2 0 1 OK")
        else -> return
    }
    emit("RUN")
}

if (setting.mode == "errors") emitError() else emitValid()
print(output)
