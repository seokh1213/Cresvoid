import kotlin.random.Random

val options = args.toList().chunked(2).associate { it[0] to it[1] }
require(options.keys.all { it in setOf("--records", "--streams", "--seed") })
val recordCount = options["--records"]?.toInt() ?: 600
val streamCount = options["--streams"]?.toInt() ?: 16
require(recordCount in 1..120_000 && streamCount in 2..4_096)
val random = Random(options["--seed"]?.toInt() ?: 1)
val output = System.out.bufferedWriter()
fun emit(command: String) { output.appendLine(command) }

fun boundaries() {
    listOf("MERGE", "AUDIT -1", "STREAM missing -1 -1", "APPEND missing -1 -1",
        "ORDER_BEGIN -1", "CONFIG 5 2 1 0 WAIT", "ORDER_BEGIN -1", "ORDER_BEGIN 1000000000001",
        "ORDER_BEGIN 3", "MERGE", "AUDIT 0", "AUDIT -1", "STREAM east -1 4",
        "STREAM east 4 4", "STREAM east 0 4", "STREAM east 0 -4", "STREAM east 0 0",
        "STREAM east 1 0", "STREAM duplicate 0 0", "STREAM mid 2 1", "STREAM late 1 3",
        "APPEND missing -1 -1", "APPEND east -1 -1", "APPEND east 0 -1",
        "APPEND east 0 256", "APPEND east 0 2", "APPEND east 0 8",
        "APPEND east 10 80", "APPEND mid 9 84", "APPEND late 8 90", "MERGE", "AUDIT 0",
        "AUDIT 1", "AUDIT 2", "APPEND east 10 80", "APPEND east 9 72",
        "APPEND east 10 81", "APPEND mid 9 85", "APPEND late 8 91", "AUDIT 1",
        "ORDER_BEGIN 1000000000001", "CONFIG 2 1 1 0 REJECT", "SHARD_CONFIG 2 2 1 0 WAIT",
        "PLACE 1 primary", "SHARD_NEXT 1 primary 1 1", "MOVE 1 primary archive",
        "LOCATE 10", "SHARD_DECODE 10", "NODE east 0", "NEXT east 1 1", "DECODE 4",
        "MERGE", "AUDIT 1", "APPEND east 11 88", "AUDIT 2", "ORDER_BEGIN 0",
        "MERGE", "STREAM east 0 0", "APPEND east 1 4", "AUDIT 0", "MERGE").forEach(::emit)

    for (layout in listOf("61 1 1 0", "1 61 1 0", "1 1 61 0", "1 1 1 9223372036854775806")) {
        val fields = layout.split(' ').map(String::toLong)
        val time = fields[3] + ((1L shl fields[0].toInt()) - 1)
        val machine = (1L shl fields[1].toInt()) - 1
        val total = (fields[0] + fields[1] + fields[2]).toInt()
        val id = Long.MAX_VALUE ushr (63 - total)
        emit("CONFIG $layout WAIT")
        emit("ORDER_BEGIN 1000000000000")
        emit("STREAM z9-near-limit $machine 0")
        emit("APPEND z9-near-limit $time $id")
        emit("APPEND z9-near-limit 0 $id")
        emit("APPEND z9-near-limit $time $id")
        emit("AUDIT 9223372036854775807")
        emit("MERGE")
    }
    emit("CONFIG 3 2 1 100 WAIT")
    emit("ORDER_BEGIN 3")
    emit("STREAM epoch-crossing 0 3")
    emit("APPEND epoch-crossing 97 0")
    emit("MERGE")
    emit("AUDIT 0")
}

boundaries()
val epoch = 1_000_000_000_000L
emit("CONFIG 40 12 11 $epoch WAIT")
emit("ORDER_BEGIN 1000000000000")
val offsets = LongArray(streamCount) { index ->
    when (index % 4) {
        0 -> -1_000_000L
        1 -> 1_000_000L
        2 -> 0L
        else -> random.nextLong(-1_000_000, 1_000_001)
    }
}
fun streamName(machine: Int): String =
    if (machine % 7 == 0) "z9-archipelago-$machine" else "node-$machine"

for (machine in 0 until streamCount) {
    emit("STREAM ${streamName(machine)} $machine ${offsets[machine]}")
}
val counts = IntArray(streamCount)
repeat(recordCount) { index ->
    val machine = if (index < streamCount) index else random.nextInt(streamCount)
    val name = streamName(machine)
    val ordinal = counts[machine]++
    val elapsed = 2_000_000L + ordinal / 4
    val sequence = ordinal % 4
    val id = (elapsed shl 23) or (machine.toLong() shl 11) or sequence.toLong()
    val actual = epoch + elapsed - offsets[machine]
    emit("APPEND $name $actual $id")
    if (index % 7 == 0) {
        when (random.nextInt(6)) {
            0 -> emit("APPEND $name $actual $id")
            1 -> emit("APPEND $name ${actual + 1} $id")
            2 -> emit("APPEND $name -1 -1")
            3 -> emit("APPEND $name $actual ${id xor (1L shl 11)}")
            4 -> emit("STREAM $name $machine ${offsets[machine]}")
            else -> emit("APPEND absent -1 -1")
        }
    }
    if (index == recordCount / 2) {
        emit("AUDIT 0")
        emit("AUDIT 2000000")
    }
}
emit("CONFIG 1 1 1 0 REJECT")
emit("ORDER_BEGIN -1")
emit("MERGE")
emit("AUDIT -1")
emit("AUDIT 0")
emit("AUDIT 1999999")
emit("AUDIT 2000000")
emit("AUDIT 1000000000000")
emit("AUDIT 0")
output.flush()
