import kotlin.random.Random

val options = args.toList().chunked(2).associate { option ->
    require(option.size == 2) { "Each option needs a value" }
    option[0] to option[1]
}
require(options.keys.all { it in setOf("--seed", "--mode", "--keys", "--commands") })
val random = Random(options["--seed"]?.toInt() ?: 1)
val mode = options["--mode"] ?: "mixed"
val keyCount = options["--keys"]?.toInt() ?: 40
val commandCount = options["--commands"]?.toInt() ?: 3000
require(keyCount in 1..120_000)
require(commandCount in 1..300_000)
require(mode in setOf("mixed", "scale"))
val output = System.out.bufferedWriter()
fun emit(line: String) {
    output.write(line)
    output.newLine()
}

fun scale() {
    val order = (0 until keyCount).shuffled(random)
    order.forEach { emit("ACQUIRE k$it a 1") }
    order.forEach { emit("STATUS k$it") }
    order.take(keyCount / 2).forEachIndexed { index, key -> emit("RELEASE k$key ${index + 1}") }
}

class MixedGenerator {
    private val keys = List(keyCount) { index ->
        when (index) {
            0 -> "task"
            1 -> "Task"
            2 -> "Z" + "9_-".repeat(10) + "q"
            else -> "job_$index"
        }
    }
    private val clients = listOf("a", "workerA", "workerB", "X9_z", "Q" + "z".repeat(31))
    private val held = mutableMapOf<String, Long>()
    private val previous = mutableMapOf<String, Long>()
    private var lastId = 0L
    private var emitted = 0

    fun generate() {
        boundarySequence()
        repeat((commandCount - emitted).coerceAtLeast(0)) {
            val key = keys.random(random)
            when (random.nextInt(10)) {
                in 0..3 -> acquire(key)
                in 4..5 -> release(key)
                in 6..7 -> send("STATUS $key")
                8 -> malformed(key)
                else -> send(" \t ")
            }
        }
    }

    private fun send(line: String) {
        if (emitted >= commandCount) return
        emit(line)
        emitted++
    }

    private fun acquire(key: String, client: String = clients.random(random), ttl: String = ttl()) {
        if (emitted >= commandCount) return
        send("ACQUIRE $key $client $ttl")
        if (key !in held) held[key] = ++lastId
    }

    private fun release(key: String, id: String = requestId(key)) {
        if (emitted >= commandCount) return
        send("RELEASE $key $id")
        if (held[key] == id.toLong()) {
            previous[key] = held.getValue(key)
            held.remove(key)
        }
    }

    private fun requestId(key: String): String {
        val id = when (random.nextInt(4)) {
            0 -> held[key] ?: 1L
            1 -> previous[key] ?: 1L
            2 -> Long.MAX_VALUE
            else -> random.nextLong(1L, lastId + 3)
        }
        return if (random.nextBoolean()) "000$id" else id.toString()
    }

    private fun ttl(): String = listOf("1", "2", "0005", "2147483648", "999999999999", "1000000000000")
        .random(random)

    private fun boundarySequence() {
        val key = keys.first()
        acquire(key, "workerA", "0001")
        acquire(key, "workerB", "1000000000000")
        send("ACQUIRE $key workerA 0")
        release(key, "0001")
        acquire(key, "workerA", "1000000000000")
        release(key, "1")
        send("STATUS $key")
        release(key, "9223372036854775807")
        send("ACQUIRE $key agent 1000000000001")
        repeat(24) { malformed(key, it) }
        send("\tSTATUS\t$key\t")
        send("\t \t")
    }

    private fun malformed(key: String, choice: Int = random.nextInt(24)) {
        val invalid = listOf(
            "ACQUIRE $key a -0", "ACQUIRE $key a -1", "ACQUIRE $key a +1",
            "ACQUIRE $key a 1.0", "ACQUIRE $key a 1e3", "ACQUIRE $key a 9223372036854775808",
            "ACQUIRE $key a -9223372036854775809", "ACQUIRE $key a --1",
            "ACQUIRE 1bad a 1", "ACQUIRE $key _bad 1", "ACQUIRE $key a.b 1",
            "ACQUIRE ${"a".repeat(33)} a 1", "RELEASE $key 0", "RELEASE $key -1",
            "RELEASE $key 9223372036854775808", "RELEASE $key +1", "STATUS $key extra",
            "STATUS", "ACQUIRE $key a", "RELEASE $key", "acquire $key a 1", "UNKNOWN",
            "STATUS $key#", "# comment",
        )
        send(invalid[choice])
    }
}

when (mode) {
    "scale" -> scale()
    "mixed" -> MixedGenerator().generate()
}
output.flush()
