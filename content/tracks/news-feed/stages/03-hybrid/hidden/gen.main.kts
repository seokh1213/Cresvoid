import kotlin.random.Random

val stage = 3
require(args.size % 2 == 0) { "Use --name value pairs" }
val options = args.toList().chunked(2).associate { it[0] to it[1] }
val allowed = setOf("--seed", "--profile", "--users", "--posts", "--commands", "--reads", "--limit", "--threshold", "--mode")
require(options.keys.all { it in allowed }) { "Unknown argument" }
fun number(name: String, default: Int): Int = options[name]?.toInt() ?: default
val random = Random(number("--seed", 1))
val profile = options["--profile"] ?: "mixed"
val mode = options["--mode"] ?: when (stage) { 1 -> "WRITE"; 2 -> "READ"; else -> "HYBRID" }
val users = number("--users", 40)
val postLimit = number("--posts", 120)
val commandLimit = number("--commands", 600)
val readLimit = number("--reads", 50_000)
val limit = number("--limit", 5)
val threshold = number("--threshold", 2)
require(profile in setOf("mixed", "scale"))
require(mode in when (stage) { 1 -> setOf("WRITE"); 2 -> setOf("WRITE", "READ"); else -> setOf("WRITE", "READ", "HYBRID") })
require(users in 6..40_000 && postLimit in 6..100_000 && commandLimit in 40..200_000)
require(readLimit in 0..50_000 && limit in 1..100 && threshold in 0..50_000)
if (profile == "mixed") {
    require(users <= 100 && commandLimit <= 5_000)
    require(commandLimit.toLong() * users * limit <= 2_000_000)
    require(commandLimit.toLong() * postLimit <= 5_000_000)
}
val output = System.out.bufferedWriter()
output.appendLine("MODE $mode $limit" + if (mode == "HYBRID") " $threshold" else "")
var commands = 0
var reads = 0
val ids = mutableListOf<Int>()
fun emit(line: String) {
    check(commands < commandLimit)
    output.appendLine(line)
    commands++
}
fun timestamp(): Long = when (random.nextInt(6)) {
    0 -> 0L
    1, 2 -> 100L
    3 -> 1_000_000_000_000L
    else -> random.nextLong(1_000_000_000_001L)
}
fun post(author: Int) {
    val serial = ids.size + 1
    val id = if (serial % 17 == 0) 1_000_000_000 - serial else serial
    ids.add(id)
    emit("POST $id $author ${timestamp()}")
}
fun feed(reader: Int) {
    if (reads < readLimit) {
        emit("FEED $reader")
        reads++
    } else emit("FOLLOW $reader $reader")
}

fun prelude() {
    feed(1)
    post(2)
    emit("FOLLOW 1 2")
    emit("FOLLOW 1 2")
    emit("FOLLOW 1 1")
    repeat(3) { post(2) }
    post(3)
    emit("FOLLOW 1 3")
    emit("POST ${ids.first()} 3 0")
    feed(1)
    if (stage < 3) return
    emit("FOLLOW 4 2")
    emit("FOLLOW 5 2")
    post(2)
    feed(1)
    emit("UNFOLLOW 5 2")
    feed(1)
    emit("UNFOLLOW 4 2")
    emit("UNFOLLOW 4 2")
    emit("UNFOLLOW 1 1")
    emit("DELETE ${ids.last()}")
    emit("DELETE ${ids.last()}")
    emit("POST ${ids.last()} 2 100")
    emit("DELETE 1000000000")
    feed(1)
    emit("UNFOLLOW 1 2")
    feed(1)
    emit("FOLLOW 1 2")
    feed(1)
}

fun mixed() {
    val userIds = (1..users - 2).toList() + listOf(999_999_937, 1_000_000_000)
    while (commands < commandLimit) {
        val reader = userIds[random.nextInt(userIds.size)]
        val author = userIds[random.nextInt(userIds.size)]
        when (random.nextInt(if (stage >= 3) 10 else 7)) {
            0, 1 -> emit("FOLLOW $reader $author")
            2, 3 -> if (ids.size < postLimit) post(author) else feed(reader)
            4 -> emit("POST ${ids[random.nextInt(ids.size)]} $author ${timestamp()}")
            5, 6 -> feed(reader)
            7 -> emit("UNFOLLOW $reader $author")
            else -> {
                val id = if (random.nextInt(4) == 0) 1_000_000_000 else ids[random.nextInt(ids.size)]
                emit("DELETE $id")
            }
        }
    }
}

fun copyReadScale() {
    val popular = 1_000_000_000
    val authorCount = 1_000
    require(users == 40_000 && postLimit == 100_000 && limit == 100 && readLimit == 50_000)
    require(mode == "WRITE" || mode == "HYBRID" && threshold == 1)
    for (reader in 10_000 until 10_000 + users) emit("FOLLOW $reader $popular")
    val popularPosts = if (mode == "WRITE") 40 else 30_000
    repeat(popularPosts) { post(popular) }
    for (author in 1_001..1_000 + authorCount) emit("FOLLOW 1 $author")
    while (ids.size < postLimit) {
        post(1_001 + (ids.size - popularPosts) % authorCount)
        if (ids.size > postLimit - readLimit) feed(1)
    }
    while (commands < commandLimit) emit("FOLLOW 10000 $popular")
    val copiedPosts = postLimit - popularPosts
    val writeCost = copiedPosts.toLong() + if (mode == "WRITE") popularPosts.toLong() * users else 0
    require(writeCost <= 2_000_000)
    require(reads.toLong() * limit <= 5_000_000)
    require(authorCount.toLong() * (authorCount + 1) / 2 + users + 2 <= 2_000_000)
}

fun scale() {
    if (mode != "READ") {
        copyReadScale()
        return
    }
    val popular = 1_000_000_000
    val isolated = 999_999_937
    require(commandLimit >= commands + users + postLimit - ids.size)
    for (reader in 1..users) emit("FOLLOW $reader $popular")
    val copied = mode == "WRITE" || mode == "HYBRID" && users <= threshold
    val popularPosts = if (copied) minOf(postLimit - ids.size, 40, 1_500_000 / users) else postLimit - ids.size
    repeat(popularPosts) { post(popular) }
    while (ids.size < postLimit) post(isolated)
    if (stage >= 3 && commandLimit - commands >= 20) {
        repeat(8) { emit("DELETE ${ids[ids.lastIndex - it]}") }
        emit("UNFOLLOW 1 $popular")
        feed(1)
        emit("FOLLOW 1 $popular")
    }
    while (commands < commandLimit) {
        if (reads < readLimit) feed(random.nextInt(1, users + 1))
        else emit("FOLLOW 1 $popular")
    }
    // COPY work and read candidates are bounded separately from the command count.
    require(popularPosts.toLong() * (if (copied) users else 0) <= 1_500_000)
    require(reads.toLong() * limit + 1_000 <= 5_000_000)
}

when (profile) {
    "mixed" -> { prelude(); mixed() }
    "scale" -> scale()
}
output.flush()
