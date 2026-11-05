import kotlin.random.Random

val stage = 1
val defaults = mapOf("nodes" to 12, "keys" to 80, "commands" to 600, "changes" to 128, "seed" to 1)
require(args.size % 2 == 0) { "Use --nodes N --keys K --commands C --changes M --seed S" }
val options = defaults.toMutableMap()
val supplied = mutableSetOf<String>()
for (index in args.indices step 2) {
    val name = args[index].removePrefix("--")
    require(args[index].startsWith("--") && name in defaults && supplied.add(name))
    options[name] = args[index + 1].toInt()
}
val maxNodes = options.getValue("nodes")
val maxKeys = options.getValue("keys")
val commandCount = options.getValue("commands")
val maxChanges = options.getValue("changes")
require(maxNodes in 4..64 && maxKeys in 16..512)
require(maxChanges in (maxNodes + 64)..256)
require(commandCount in (maxNodes + maxKeys + 256)..4096)
val random = Random(options.getValue("seed"))
val lines = mutableListOf<String>()
val nodes = linkedMapOf<String, Int>()
val registeredKeys = linkedSetOf<String>()
var changes = 0

val nodeNames = (listOf("a", "b", "node309", "node1120", "vnode4", "edge5577", "edge26470", "qa_rare") +
    (0 until 64).map { "shard-${it}_${random.nextInt(10000)}" }).take(maxNodes)
val keyNames = (listOf(
    "apple", "a", "b", "node309", "node1120", "edge5577", "edge8403", "edge26470",
    "edge188131", "alpha", "gamma", "vnode4", "a-b", "a_b", "z", "abcdefghijklmnopqrstuvwx"
) + (0 until 512).map { "item_${it}_${random.nextInt(10000)}" }).take(maxKeys)

fun emit(command: String) {
    val decorated = when (random.nextInt(5)) {
        0 -> " \t" + command.replace(" ", "\t  ") + "\t "
        else -> command
    }
    lines.add(decorated + if (random.nextInt(7) == 0) "\r" else "")
}

fun add(node: String, capacity: Int? = null) {
    check(node !in nodes && nodes.size < maxNodes && changes < maxChanges)
    if (capacity == null) emit("ADD $node") else emit("VADD $node $capacity")
    nodes[node] = if (capacity == null) 1 else 16 * capacity
    changes++
}

fun remove(node: String, replicas: Int? = null) {
    check(node in nodes && changes < maxChanges)
    if (replicas == null) emit("REMOVE $node") else emit("FAIL $node $replicas")
    nodes.remove(node)
    changes++
}

fun weight(node: String, capacity: Int, spelling: String = capacity.toString()) {
    check(node in nodes && changes < maxChanges)
    emit("WEIGHT $node $spelling")
    nodes[node] = 16 * capacity
    changes++
}

fun put(key: String) {
    registeredKeys.add(key)
    check(registeredKeys.size <= maxKeys)
    emit("PUT $key")
}

fun clearNodes() {
    nodes.keys.toList().forEach { remove(it) }
}

fun ringBoundaries() {
    emit("")
    if (stage >= 2) emit("LOAD")
    add("empty")
    if (stage >= 2) emit("LOAD")
    remove("empty", if (stage >= 3) 8 else null)
    emit("HASH edge5577")
    emit("HASH edge26470")
    emit("OWNER absent")
    put("apple")
    if (stage >= 2) emit("LOAD")
    if (stage >= 3) emit("REPLICAS apple 08")
    add("b")
    put("a")
    put("b")
    add("a")
    emit("ADD a")
    emit("ADD a extra")
    emit("OWNER apple")
    clearNodes()
    add("node309")
    add("node1120")
    put("node1120")
    put("node309")
    if (stage >= 3) emit("REPLICAS node309 2")
    remove("node1120")
    clearNodes()
    add("edge5577")
    add("edge26470")
    for (key in listOf("edge5577", "edge8403", "edge26470", "edge188131")) put(key)
    emit("OWNER apple")
    clearNodes()
}

fun capacityBoundaries() {
    if (stage < 2) return
    emit("LOAD")
    add("a")
    add("b", 2)
    weight("a", 1, "000000000000000000000000000000000001")
    weight("a", 1, "01")
    put("gamma")
    put("alpha")
    add("vnode4", 8)
    emit("HASH vnode4")
    emit("LOAD")
    weight("vnode4", 1)
    weight("vnode4", 8)
    emit("VADD a 0")
    emit("VADD a 2")
    emit("WEIGHT absent 2")
    emit("WEIGHT a +1")
    emit("LOAD")
    clearNodes()
}

fun replicationBoundaries() {
    if (stage < 3) return
    add("a", 1)
    add("b", 2)
    add("c", 1)
    emit("REPLICAS alpha 1")
    emit("REPLICAS gamma 2")
    emit("REPLICAS alpha 08")
    emit("FAIL b 0")
    remove("b", 2)
    emit("REPLICAS alpha 2")
    remove("a", 8)
    remove("c", 1)
    emit("REPLICAS b 8")
    emit("FAIL c 1")
    add("b")
    remove("b", 1)
}

val invalidCommands = mutableListOf(
    "UNKNOWN a", "add a", "HASH", "PUT a extra", "ADD A", "REMOVE absent",
    "OWNER absent", "HASH a#0", "HASH a.b", "HASH 0a", "ADD a\u00a0b", "PUT a\u000bb",
    "PUT abcdefghijklmnopqrstuvwxy", "OWNER _a", "REMOVE", "HASH a b"
)
if (stage >= 2) {
    invalidCommands += listOf(
        "VADD a", "WEIGHT a 9", "VADD absent 00", "WEIGHT absent 999999999999999999999999",
        "VADD a 1.0", "WEIGHT a -1", "VADD a １", "LOAD a", "VADD a 1 extra", "WEIGHT A 1"
    )
}
if (stage >= 3) {
    invalidCommands += listOf(
        "REPLICAS apple 0", "FAIL absent 9", "REPLICAS absent 1", "FAIL absent 1",
        "REPLICAS apple +1", "FAIL a 1e0", "FAIL a 2 extra", "REPLICAS apple １"
    )
}

ringBoundaries()
capacityBoundaries()
replicationBoundaries()
invalidCommands.forEach(::emit)
for (node in nodeNames) add(node, if (stage >= 2) 8 else null)
for (key in keyNames) put(key)
if (stage >= 2) emit("LOAD")
if (stage >= 3) emit("REPLICAS apple 8")
check(lines.size <= commandCount)

fun randomChange() {
    val absent = nodeNames.filter { it !in nodes }
    val existing = nodes.keys.toList()
    when {
        absent.isNotEmpty() && (nodes.size < maxNodes / 2 || random.nextBoolean()) -> {
            val capacity = if (stage >= 2 && random.nextBoolean()) random.nextInt(1, 9) else null
            add(absent.random(random), capacity)
        }
        stage >= 2 && existing.isNotEmpty() && random.nextBoolean() -> {
            val node = existing.random(random)
            val currentCapacity = nodes.getValue(node) / 16
            val capacity = if (currentCapacity > 0 && random.nextBoolean()) currentCapacity else random.nextInt(1, 9)
            weight(node, capacity)
        }
        existing.isNotEmpty() -> {
            val replicas = if (stage >= 3 && random.nextBoolean()) random.nextInt(1, 9) else null
            remove(existing.random(random), replicas)
        }
        else -> add(absent.random(random))
    }
}

while (lines.size < commandCount) {
    val key = keyNames.random(random)
    when (random.nextInt(10)) {
        0, 1, 2 -> if (changes < maxChanges) randomChange() else emit("OWNER $key")
        3 -> put(key)
        4 -> emit("HASH ${if (random.nextBoolean()) key else nodeNames.random(random)}")
        5 -> emit(invalidCommands.random(random))
        6 -> if (stage >= 2) emit("LOAD") else emit("OWNER $key")
        7 -> if (stage >= 3) emit("REPLICAS $key ${random.nextInt(1, 9)}") else emit("OWNER $key")
        8 -> if (nodes.isNotEmpty()) emit("ADD ${nodes.keys.random(random)}") else emit("REMOVE absent")
        else -> if (random.nextInt(4) == 0) emit("") else emit("OWNER $key")
    }
}
System.out.bufferedWriter().use { output -> lines.forEach { output.appendLine(it) } }
