import kotlin.random.Random

val options = args.toList().chunked(2).associate { option ->
    require(option.size == 2) { "Each option needs a value" }
    option[0] to option[1]
}
require(options.keys.all { it in setOf("--seed", "--mode", "--keys", "--commands") })
val random = Random(options["--seed"]?.toInt() ?: 1)
val mode = options["--mode"] ?: "mixed"
val keyCount = options["--keys"]?.toInt() ?: 40
val commandCount = options["--commands"]?.toInt() ?: 5000
require(keyCount in 1..120_000)
require(commandCount in 1..300_000)
require(mode in setOf("mixed", "scale"))
if (mode == "scale") require(keyCount <= 100_000)
val output = System.out.bufferedWriter()
fun emit(line: String) {
    output.write(line)
    output.newLine()
}

fun scale() {
    val order = (0 until keyCount).shuffled(random)
    val expiringKeyCount = (keyCount / 2).coerceAtLeast(1)
    order.forEachIndexed { index, key ->
        val ttl = if (index < expiringKeyCount) index + 1L else 1_000_000_000_000L
        emit("ACQUIRE k$key a $ttl")
    }
    repeat(keyCount) { index ->
        val key = order[index % expiringKeyCount]
        emit("ADVANCE ${index + 1}")
        emit("ACQUIRE k$key a $expiringKeyCount")
    }
}

data class Held(val acquisitionId: Long, val expiresAt: Long)

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
    private val held = mutableMapOf<String, Held>()
    private val previous = mutableMapOf<String, Long>()
    private var lastId = 0L
    private var now = 0L
    private var emitted = 0

    fun generate() {
        boundarySequence()
        repeat((commandCount - emitted - 8).coerceAtLeast(0)) {
            val key = keys.random(random)
            when (random.nextInt(12)) {
                in 0..2 -> acquire(key)
                3 -> release(key, requestId(key))
                in 4..5 -> renew(key, requestId(key), ttl())
                6 -> send("STATUS $key")
                in 7..8 -> moveNearExpiry(key)
                9 -> malformed(key)
                10 -> send("ADVANCE ${now - 1}")
                else -> send(" \t ")
            }
        }
        maximumTimeSequence()
    }

    private fun send(line: String): Boolean {
        if (emitted >= commandCount) return false
        emit(line)
        emitted++
        return true
    }

    private fun acquire(key: String, ttl: String = ttl()) {
        if (!send("ACQUIRE $key ${clients.random(random)} $ttl")) return
        if (key !in held) held[key] = Held(++lastId, now + ttl.toLong())
    }

    private fun release(key: String, id: Long) {
        if (!send("RELEASE $key ${number(id)}")) return
        if (held[key]?.acquisitionId == id) {
            previous[key] = id
            held.remove(key)
        }
    }

    private fun renew(key: String, id: Long, ttl: String) {
        if (!send("RENEW $key ${number(id)} $ttl")) return
        val current = held[key] ?: return
        if (current.acquisitionId == id) held[key] = current.copy(expiresAt = now + ttl.toLong())
    }

    private fun advance(time: Long) {
        if (!send("ADVANCE ${number(time)}")) return
        if (time !in now..1_000_000_000_000L) return
        now = time
        val expired = held.filterValues { it.expiresAt <= now }
        expired.forEach { (key, lease) ->
            previous[key] = lease.acquisitionId
            held.remove(key)
        }
    }

    private fun moveNearExpiry(key: String) {
        val expiry = held[key]?.expiresAt
        val candidate = if (expiry != null && random.nextBoolean()) {
            expiry + random.nextLong(-1L, 2L)
        } else {
            now + random.nextLong(0L, 4L)
        }
        advance(candidate.coerceIn(now, 1_000_000_000_000L))
    }

    private fun requestId(key: String): Long = when (random.nextInt(4)) {
        0 -> held[key]?.acquisitionId ?: 1L
        1 -> previous[key] ?: 1L
        2 -> Long.MAX_VALUE
        else -> random.nextLong(1L, lastId + 3)
    }

    private fun number(value: Long): String = if (value >= 0 && random.nextBoolean()) "000$value" else "$value"

    private fun ttl(): String = listOf("1", "2", "0005", "10", "2147483648", "1000000000000")
        .random(random)

    private fun boundarySequence() {
        val key = keys.first()
        acquire(key, "10")
        acquire(key, "20")
        advance(9)
        renew(key, 1, "5")
        advance(10)
        renew(key, 1, "4")
        renew(key, 1, "4")
        advance(13)
        advance(14)
        renew(key, 1, "5")
        acquire(key, "1")
        release(key, 1)
        renew(key, 1, "20")
        advance(15)
        acquire(key, "20")
        advance(16)
        renew(key, 3, "1")
        send("ADVANCE 15")
        send("RENEW $key 3 0")
        send("STATUS $key")
        advance(17)
        advance(17)
        advance(40)
        repeat(31) { malformed(key, it) }
        send("\tSTATUS\t$key\t")
        send("\t \t")
    }

    private fun maximumTimeSequence() {
        val key = keys.first()
        advance(1_000_000_000_000L)
        held[key]?.let { release(key, it.acquisitionId) } ?: send("STATUS $key")
        acquire(key, "1000000000000")
        val id = held[key]?.acquisitionId ?: 1L
        renew(key, id, "1000000000000")
        send("ADVANCE 1000000000001")
        send("RENEW $key $id 1000000000001")
        send("STATUS $key")
        advance(1_000_000_000_000L)
    }

    private fun malformed(key: String, choice: Int = random.nextInt(31)) {
        val invalid = listOf(
            "ACQUIRE $key a -0", "ACQUIRE $key a -1", "ACQUIRE $key a +1",
            "ACQUIRE $key a 1.0", "ACQUIRE $key a 1e3", "ACQUIRE $key a 9223372036854775808",
            "ACQUIRE $key a -9223372036854775809", "ACQUIRE $key a --1",
            "ACQUIRE 1bad a 1", "ACQUIRE $key _bad 1", "ACQUIRE $key a.b 1",
            "ACQUIRE ${"a".repeat(33)} a 1", "RELEASE $key 0", "RELEASE $key -1",
            "RELEASE $key 9223372036854775808", "RELEASE $key +1", "STATUS $key extra",
            "STATUS", "ACQUIRE $key a", "RELEASE $key", "acquire $key a 1", "UNKNOWN",
            "STATUS $key#", "# comment", "ADVANCE -1", "ADVANCE 1000000000001",
            "ADVANCE +1", "ADVANCE 9223372036854775808", "RENEW $key 1 0",
            "RENEW $key 0 1", "RENEW $key 1 1 extra",
        )
        send(invalid[choice])
    }
}

when (mode) {
    "scale" -> scale()
    "mixed" -> MixedGenerator().generate()
}
output.flush()
