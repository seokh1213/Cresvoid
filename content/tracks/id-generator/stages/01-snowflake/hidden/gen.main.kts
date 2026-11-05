import kotlin.random.Random

val options = args.toList().chunked(2).associate { it[0] to it[1] }
require(options.keys.all { it in setOf("--commands", "--seed") })
val commandLimit = options["--commands"]?.toInt() ?: 1_000
require(commandLimit in 512..30_000)
val random = Random(options["--seed"]?.toInt() ?: 1)
val commands = mutableListOf<String>()
fun emit(command: String) {
    if (commands.size < commandLimit) commands.add(command)
}

fun boundaries() {
    listOf("NODE east 0", "NEXT missing 1 -1", "DECODE -1", "CONFIG 0 1 1 0 WAIT").forEach(::emit)
    val layouts = listOf(
        "3 2 1 100", "2 1 2 10", "61 1 1 0", "1 61 1 0", "1 1 61 0",
        "1 1 1 9223372036854775806", "41 10 12 0",
    )
    for (layout in layouts) {
        val fields = layout.split(' ').map(String::toLong)
        val epoch = fields[3]
        val end = epoch + ((1L shl fields[0].toInt()) - 1)
        val maxMachine = (1L shl fields[1].toInt()) - 1
        for (policy in listOf("WAIT", "REJECT")) {
            emit("CONFIG $layout $policy")
            emit("NODE east 0")
            emit("NODE z9-ultraviolet-archipelago $maxMachine")
            emit("NODE east -1")
            emit("NODE east 0")
            emit("NODE spare 0")
            emit("NODE overflow ${maxMachine + 1}")
            emit("NEXT missing 1 -1")
            emit("NEXT east 2 $epoch -1")
            emit("NEXT east 1 $epoch")
            emit("NEXT east 1 $epoch")
            emit("NEXT east 3 $epoch $epoch $end")
            emit("NEXT east 2 $end $epoch")
            emit("NEXT east 3 $epoch $end $end")
            emit("NEXT z9-ultraviolet-archipelago 1 $end")
            listOf(-1L, 0L, 1L, Long.MAX_VALUE).forEach { emit("DECODE $it") }
            emit("CONFIG 9223372036854775807 2 1 0 WAIT")
            emit("CONFIG 62 1 1 0 WAIT")
            emit("CONFIG 1 1 1 9223372036854775807 WAIT")
            emit("CONFIG 1 1 1 -1 WAIT")
            emit("NEXT east 1 $end")
        }
    }
    for (policy in listOf("REJECT", "WAIT")) {
        emit("CONFIG 4 2 1 100 $policy")
        emit("NODE east 0")
        emit("NEXT east 1 102")
        emit("NEXT east 1 102")
        emit("NEXT east 3 102 101 103")
        emit("NEXT east 1 103")
        emit("NEXT east 2 104 99")
        emit("NEXT east 1 104")
        emit("NEXT east 1 104")
        emit("NEXT east 4096 ${List(4096) { "104" }.joinToString(" ")}")
        emit("NEXT east 3 100 104 105")
    }
}

boundaries()
listOf("1 0 1 0", "1 1 0 0", "-1 1 1 0", "1 -1 1 0", "1 1 -1 0",
    "1 9223372036854775807 1 0", "1 1 -9223372036854775808 0").forEach {
    emit("CONFIG $it WAIT")
}
var cycle = 0
while (commands.size < commandLimit) {
    val epoch = listOf(0L, 100L, 1_700_000_000_000L).random(random)
    val policy = if (random.nextBoolean()) "WAIT" else "REJECT"
    emit("CONFIG 8 4 2 $epoch $policy")
    val names = listOf("east", "west", "z9-rare", "a1234567890123456789012345678901")
    names.forEachIndexed { index, name -> emit("NODE $name $index") }
    repeat(minOf(400, commandLimit - commands.size)) {
        val machine = random.nextInt(6)
        val name = names.getOrElse(machine) { "missing" }
        when (random.nextInt(10)) {
            0 -> emit("NODE $name ${random.nextLong(-1, 18)}")
            1 -> emit("DECODE ${listOf(-1L, 0L, 16_383L, 16_384L, Long.MAX_VALUE).random(random)}")
            2 -> emit("CONFIG ${listOf("0 4 2 0", "61 4 2 0", "1 1 1 -1").random(random)} $policy")
            else -> {
                val time = epoch + ((cycle + it / 5) % 256)
                val samples = List(random.nextInt(1, 7)) {
                    when (random.nextInt(8)) {
                        0 -> epoch - 1
                        1 -> epoch + 256
                        2 -> epoch
                        3 -> epoch + 255
                        else -> time
                    }
                }
                emit("NEXT $name ${samples.size} ${samples.joinToString(" ")}")
            }
        }
    }
    cycle++
}
System.out.bufferedWriter().use { output -> commands.forEach(output::appendLine) }
