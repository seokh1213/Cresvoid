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

baseCases()
for (user in 1..users) emit("SET $user ${score()}")
repeat(commands) { index ->
    if (profile == "mixed") mixedCommand()
    else {
        val user = random.nextInt(users) + 1
        when (index % 4) {
            0 -> emit("SET $user ${score()}")
            1 -> emit("ADD $user -1")
            2 -> emit("RANK $user")
            else -> emit("ADD $user 0")
        }
    }
}
output.flush()
