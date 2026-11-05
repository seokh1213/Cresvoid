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
    listOf("PLACE -1 db-a", "MOVE 0 db-a db-b", "SHARD_NEXT 0 db-a 1 -1", "LOCATE -1",
        "SHARD_DECODE 0", "NODE east 0", "SHARD_CONFIG 0 1 1 0 WAIT").forEach(::emit)
    val layouts = listOf("3 2 1 100", "61 1 1 0", "1 61 1 0", "1 1 61 0",
        "1 1 1 9223372036854775806", "41 13 9 0")
    for (layout in layouts) {
        val fields = layout.split(' ').map(String::toLong)
        val epoch = fields[3]
        val end = epoch + ((1L shl fields[0].toInt()) - 1)
        val maxShard = (1L shl fields[1].toInt()) - 1
        for (policy in listOf("REJECT", "WAIT")) {
            emit("SHARD_CONFIG $layout $policy")
            emit("PLACE 0 db-a")
            emit("PLACE $maxShard z9-cold-archive")
            emit("PLACE -1 db-a")
            emit("PLACE ${maxShard + 1} db-a")
            emit("PLACE 0 db-a")
            emit("SHARD_NEXT -1 wrong 1 -1")
            emit("MOVE ${maxShard + 1} db-a db-b")
            emit("SHARD_NEXT 0 wrong 1 -1")
            emit("SHARD_NEXT 0 db-a 2 $epoch -1")
            emit("SHARD_NEXT 0 db-a 1 $epoch")
            emit("MOVE 0 wrong db-b")
            emit("MOVE 0 db-a db-a")
            emit("SHARD_NEXT 0 db-a 1 $epoch")
            emit("MOVE 0 db-a db-b")
            emit("SHARD_NEXT 0 db-a 1 $end")
            emit("SHARD_NEXT 0 db-b 3 $epoch $epoch $end")
            emit("SHARD_NEXT 0 db-b 2 $epoch $end")
            emit("CONFIG 2 1 1 0 WAIT")
            emit("NODE east 0")
            emit("NEXT east 1 1")
            emit("SHARD_CONFIG 9223372036854775807 1 1 0 WAIT")
            emit("SHARD_CONFIG 62 1 1 0 WAIT")
            emit("SHARD_CONFIG 1 1 1 -1 WAIT")
            emit("SHARD_CONFIG 1 1 1 9223372036854775807 WAIT")
            listOf(-1L, 0L, 1L, 2L, Long.MAX_VALUE).forEach {
                emit("LOCATE $it")
                emit("SHARD_DECODE $it")
                emit("DECODE $it")
            }
            emit("NEXT east 1 1")
        }
    }
    emit("SHARD_CONFIG 4 3 1 100 WAIT")
    emit("MOVE 7 absent db-a")
    emit("SHARD_NEXT 7 absent 1 -1")
    emit("PLACE 0 primary")
    emit("PLACE 1 primary")
    emit("SHARD_NEXT 0 primary 1 102")
    emit("MOVE 0 primary secondary")
    emit("SHARD_NEXT 0 secondary 1 102")
    emit("MOVE 0 secondary primary")
    emit("SHARD_NEXT 0 primary 4096 ${List(4096) { "102" }.joinToString(" ")}")
    emit("SHARD_NEXT 0 primary 3 101 102 103")
    emit("SHARD_NEXT 1 primary 1 102")
}

boundaries()
listOf("1 0 1 0", "1 1 0 0", "-1 1 1 0", "1 -1 1 0", "1 1 -1 0",
    "1 9223372036854775807 1 0", "1 1 -9223372036854775808 0").forEach {
    emit("SHARD_CONFIG $it WAIT")
}
while (commands.size < commandLimit) {
    val epoch = listOf(0L, 100L, 1_700_000_000_000L).random(random)
    emit("SHARD_CONFIG 8 4 2 $epoch ${if (random.nextBoolean()) "WAIT" else "REJECT"}")
    emit("CONFIG 8 4 2 $epoch WAIT")
    emit("NODE east 0")
    val servers = listOf("primary", "db-b", "z9-cold-archive")
    val owners = MutableList(4) { "primary" }
    repeat(4) { emit("PLACE $it primary") }
    repeat(minOf(400, commandLimit - commands.size)) { step ->
        val shard = random.nextInt(-1, 7)
        val owner = owners.getOrNull(shard) ?: "primary"
        when (random.nextInt(12)) {
            0 -> emit("PLACE $shard ${servers.random(random)}")
            1, 2 -> {
                val from = if (random.nextInt(4) == 0) "wrong" else owner
                val to = servers.random(random)
                emit("MOVE $shard $from $to")
                if (shard in owners.indices && from == owner) owners[shard] = to
            }
            3 -> emit("LOCATE ${random.nextLong(-1, 16_385)}")
            4 -> emit("SHARD_DECODE ${listOf(-1L, 0L, 16_383L, 16_384L, Long.MAX_VALUE).random(random)}")
            5 -> emit("NEXT east 1 ${epoch + step / 5}")
            6 -> emit("DECODE ${random.nextLong(-1, 16_385)}")
            7 -> emit("SHARD_CONFIG 61 4 2 0 WAIT")
            else -> {
                val samples = List(random.nextInt(1, 7)) {
                    epoch + listOf(-1L, 0L, step.toLong() / 5, 255L, 256L).random(random)
                }
                val server = if (random.nextInt(5) == 0) "wrong" else owner
                emit("SHARD_NEXT $shard $server ${samples.size} ${samples.joinToString(" ")}")
            }
        }
    }
}
System.out.bufferedWriter().use { output -> commands.forEach(output::appendLine) }
