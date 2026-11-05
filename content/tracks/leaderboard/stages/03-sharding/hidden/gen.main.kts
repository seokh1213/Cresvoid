import kotlin.random.Random

val options = args.toList().chunked(2).associate { pair ->
    require(pair.size == 2) { "옵션은 --이름 값 쌍이어야 한다" }
    pair[0] to pair[1]
}
require(options.keys.all { it in setOf("--users", "--commands", "--profile", "--seed") })
val users = (options["--users"] ?: "200").toInt().also { require(it in 1..2_000_000) }
val commands = (options["--commands"] ?: "1000").toInt().also { require(it in 0..100_000) }
val profile = options["--profile"] ?: "mixed"
require(profile in setOf("mixed", "scale"))
val random = Random((options["--seed"] ?: "1").toInt())
val output = System.out.bufferedWriter()

fun emit(line: String) {
    output.write(line)
    output.newLine()
}

fun score(): Long = when (random.nextInt(10)) {
    0 -> -1_000_000_000_000L
    1 -> 1_000_000_000_000L
    2, 3 -> 0L
    4, 5 -> 100L
    else -> random.nextLong(-1000, 1001)
}

fun baseCases() {
    listOf(
        "", " \t ", "RANK 2147483647", "SET 20 100", "SET 3 100", "RANK 3",
        "SET 20 100", "ADD 3 0", "RANK 20", "ADD 20 -20", "ADD 20 20", "RANK 20",
        "ADD 7 0", "SET 2 -5", "RANK 7", "SET 9 1000000000000", "ADD 9 1", "RANK 9",
        "SET 11 -1000000000000", "ADD 11 -1", "RANK 11", "SET 2147483647 0", "RANK 2147483647",
        "SET 1 01", "ADD 1 -0", "SET 1 +1", "SET 1 1.0", "SET 1 1e2", "SET 1 1000000000001",
        "SET 0 1", "SET 2147483648 1", "RANK -1", "ADD 1 999999999999999999999999",
        "SET 1", "RANK 1 extra", "BOGUS", "set 1 1", "\t SET\t12\t-7 \t", "RANK 12"
    ).forEach(::emit)
}

fun mixedCommand() {
    val user = random.nextInt(users) + 1
    when (random.nextInt(12)) {
        0, 1, 2 -> emit("SET $user ${score()}")
        3, 4 -> emit("ADD $user ${random.nextLong(-200, 201)}")
        5 -> emit("ADD $user 0")
        6, 7 -> emit("RANK $user")
        8 -> emit("RANK 2147483646")
        9 -> emit("SET $user ${listOf("01", "-0", "+2", "1000000000001").random(random)}")
        10 -> emit("ADD $user ${score()}")
        else -> emit(" \t ")
    }
}

fun rangeCases() {
    listOf(
        "TOP 0", "TOP 1000", "AROUND 20 0", "AROUND 3 500", "AROUND 2147483646 1",
        "RESET all", "TOP 3", "TICK 7", "CREATE daily 10", "CREATE permanent 0",
        "CREATE z123456789012345 1000000000000", "BSET daily 20 8", "BSET daily 3 8",
        "BTOP daily 5", "BAROUND daily 20 1", "BRANK all 20", "BADD all 5 0",
        "TICK 9", "RESET daily", "BADD daily 3 2", "BADD daily 20 2", "BAROUND daily 20 1",
        "TICK 10", "BRANK daily 3", "BADD daily 20 0", "TICK 9", "BRANK daily 20",
        "BSET permanent 8 -1", "TICK 10", "TICK 30", "BTOP daily 1", "BTOP permanent 3",
        "CREATE all 1", "CREATE daily 0", "CREATE Bad 2", "CREATE a1234567890123456 1",
        "CREATE a-b 1", "CREATE zero -0", "CREATE future 1000000000001", "TICK -1",
        "TOP 1001", "AROUND 1 501", "AROUND 1 -1", "TOP 01", "BTOP missing 0",
        "BRANK missing 1", "BSET missing 1 0", "BADD daily 0 1", "BAROUND missing 1 0",
        "BTOP daily 1 extra", "RESET missing", "RESET", "TICK", "BSET daily 1",
        "RESET all", "RESET daily", "RESET permanent", "RESET z123456789012345"
    ).forEach(::emit)
}

var time = 30L
val boardNames = listOf("all", "daily", "permanent", "z123456789012345")

fun rangeCommand() {
    val user = random.nextInt(users) + 1
    val board = boardNames.random(random)
    when (random.nextInt(18)) {
        0, 1, 2 -> mixedCommand()
        3 -> emit("BSET $board $user ${score()}")
        4 -> emit("BADD $board $user ${random.nextLong(-100, 101)}")
        5 -> emit("BRANK $board $user")
        6 -> emit("TOP ${random.nextInt(5)}")
        7 -> emit("AROUND $user ${random.nextInt(3)}")
        8 -> emit("BTOP $board ${random.nextInt(5)}")
        9 -> emit("BAROUND $board $user ${random.nextInt(3)}")
        10 -> {
            time += random.nextLong(0, 26)
            emit("TICK $time")
        }
        11 -> emit("TICK ${time - 1}")
        12 -> emit("RESET ${if (profile == "scale") "daily" else board}")
        13 -> emit("CREATE $board ${random.nextInt(3)}")
        14 -> emit("BSET $board $user 01")
        15 -> emit("BTOP missing 0")
        16 -> emit("BAROUND $board 2147483646 0")
        else -> emit("BADD $board $user ${score()}")
    }
}

fun distributionCases() {
    listOf(
        "DRANK all RANGE 1", "DCOUNTS all HASH", "SET 8 100", "SET 2 100", "SET 6 120",
        "SET 4 -5", "SET 5 0", "DISTRIBUTE all 3 0 100", "DCOUNTS all RANGE", "DCOUNTS all HASH",
        "DRANK all RANGE 2", "DRANK all HASH 2", "DRANK all RANGE 5", "DRANK all HASH 5",
        "ADD 8 -101", "DCOUNTS all RANGE", "DRANK all HASH 8", "DRANK all RANGE 2147483646",
        "DISTRIBUTE all 3 10 10", "DISTRIBUTE all 3 10 0", "DISTRIBUTE all 1 0",
        "DISTRIBUTE all 0", "DISTRIBUTE all 33", "DISTRIBUTE all 2", "DISTRIBUTE all 2 -0",
        "DISTRIBUTE all 2 1000000000001", "DISTRIBUTE all 2 999999999999999999999999",
        "DISTRIBUTE all 2 +1", "DISTRIBUTE missing 1", "DCOUNTS all range", "DRANK all HASH 0",
        "DCOUNTS missing RANGE", "DRANK all HASH", "DCOUNTS all HASH extra",
        "DCOUNTS all RANGE", "DISTRIBUTE all 3 -1000000000000 1000000000000",
        "SET 1 -1000000000000", "SET 9 1000000000000", "ADD 9 1", "ADD 1 -1",
        "DCOUNTS all RANGE", "DRANK all HASH 1", "DISTRIBUTE all 1", "DRANK all HASH 9",
        "DISTRIBUTE daily 2 0", "BADD daily 7 0", "TICK 40", "DCOUNTS daily RANGE",
        "BADD daily 8 0", "DRANK daily HASH 8", "RESET daily", "DCOUNTS daily HASH",
        "RESET all", "DCOUNTS all RANGE", "DISTRIBUTE permanent 32 " + (1..31).joinToString(" ") { (it - 16).toString() }
    ).forEach(::emit)
    time = 40L
}

fun configure(board: String, shards: Int) {
    val boundaries = (1 until shards).joinToString(" ") { ((it - shards / 2) * 100).toString() }
    emit("DISTRIBUTE $board $shards${if (boundaries.isEmpty()) "" else " $boundaries"}")
}

fun shardCommand() {
    val user = random.nextInt(users) + 1
    val board = boardNames.random(random)
    val mode = if (random.nextBoolean()) "RANGE" else "HASH"
    when (random.nextInt(10)) {
        0, 1, 2 -> rangeCommand()
        3, 4, 5 -> emit("DRANK all $mode $user")
        6 -> emit("DCOUNTS $board $mode")
        7 -> emit("DRANK $board $mode $user")
        8 -> {
            val target = if (profile == "scale") "daily" else board
            configure(target, listOf(1, 2, 3, 7, 32).random(random))
        }
        else -> emit("DISTRIBUTE $board 3 0 0")
    }
}

baseCases()
rangeCases()
distributionCases()
configure("all", 32)
for (user in 1..users) emit("SET $user ${score()}")
if (profile == "scale") configure("all", 32)
repeat(commands) { index ->
    if (profile == "mixed") shardCommand()
    else {
        val user = random.nextInt(users) + 1
        when (index % 4) {
            0 -> emit("SET $user ${score()}")
            1 -> emit("ADD $user -1")
            2 -> emit("DRANK all ${if (index % 8 == 2) "HASH" else "RANGE"} $user")
            else -> shardCommand()
        }
    }
}
emit("TICK 1000000000000")
emit("BRANK z123456789012345 1")
emit("BTOP permanent 3")
output.flush()
