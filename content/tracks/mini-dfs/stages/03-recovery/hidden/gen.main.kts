import kotlin.random.Random

class Options(val nodes: Int, val racks: Int, val files: Int, val queries: Int, val seconds: Long, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf(
        "--nodes" to "6",
        "--racks" to "3",
        "--files" to "40",
        "--queries" to "40",
        "--seconds" to "4000",
        "--seed" to "1",
    )
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        nodes = values.getValue("--nodes").toInt(),
        racks = values.getValue("--racks").toInt(),
        files = values.getValue("--files").toInt(),
        queries = values.getValue("--queries").toInt(),
        seconds = values.getValue("--seconds").toLong(),
        seed = values.getValue("--seed").toLong(),
    )
}

val options = parseOptions(args)
val random = Random(options.seed)
val largeScale = options.files >= 10_000
val output = StringBuilder()

fun emit(line: String) {
    output.append(line).append('\n')
}

fun chance(probability: Double): Boolean = random.nextDouble() < probability

fun <T> pick(items: List<T>): T = items[random.nextInt(items.size)]

fun skewedIndex(size: Int): Int = (size * random.nextDouble() * random.nextDouble()).toInt().coerceIn(0, size - 1)

fun <T> weighted(vararg choices: Pair<T, Int>): T {
    var target = random.nextInt(choices.sumOf { it.second })
    for ((value, weight) in choices) {
        if (target < weight) return value
        target -= weight
    }
    error("가중치 합이 맞지 않는다")
}

val STALE_INTERVAL = 30L
val RECHECK_INTERVAL = 300L
val DEAD_INTERVAL = 630L
val SAFE_MODE_EXTENSION = 30L

class NodeSpec(val name: String, val rack: String, val capacity: Long) {
    var running = true
    var stoppedAt = 0L
    var dead = false
    var returnAt: Long? = null
}

class Clock {
    var now = 0L
    var ticked = 0L
}

val clock = Clock()

class SafeMode {
    var on = false
    var exitAt: Long? = null
}

val safeMode = SafeMode()

class Cluster {
    val nodes = LinkedHashMap<String, NodeSpec>()
    val pending = ArrayDeque<NodeSpec>()

    val running: List<NodeSpec> get() = nodes.values.filter { it.running }
    val stopped: List<NodeSpec> get() = nodes.values.filter { !it.running }

    fun register(spec: NodeSpec) {
        emit("REGISTER ${spec.name} ${spec.rack} ${spec.capacity}")
        if (spec.name !in nodes) nodes[spec.name] = spec
    }

    fun randomNode(): NodeSpec? = if (nodes.isEmpty()) null else nodes.values.elementAt(random.nextInt(nodes.size))

    fun stop(node: NodeSpec) {
        emit("STOP ${node.name}")
        if (!node.running) return
        node.running = false
        node.stoppedAt = clock.now
    }

    fun start(node: NodeSpec) {
        emit("START ${node.name}")
        if (node.running) return
        node.running = true
        node.dead = false
        node.returnAt = null
        if (safeMode.on) checkThreshold()
    }

    fun checkThreshold() {
        if (safeMode.on && safeMode.exitAt == null && stopped.isEmpty()) safeMode.exitAt = clock.now + SAFE_MODE_EXTENSION
    }

    fun restart() {
        emit("RESTART")
        safeMode.on = true
        safeMode.exitAt = null
        checkThreshold()
    }

    fun tick(seconds: Long) {
        emit("TICK $seconds")
        val end = clock.now + seconds
        clock.ticked += seconds
        while (clock.now < end) {
            val nextJudgment = (clock.now / RECHECK_INTERVAL + 1) * RECHECK_INTERVAL
            val nextExit = safeMode.exitAt?.takeIf { it > clock.now } ?: Long.MAX_VALUE
            clock.now = minOf(end, nextJudgment, nextExit)
            if (safeMode.on && safeMode.exitAt == clock.now) {
                safeMode.on = false
                safeMode.exitAt = null
            }
            if (clock.now % RECHECK_INTERVAL == 0L && !safeMode.on) {
                stopped.filter { !it.dead && clock.now - it.stoppedAt > DEAD_INTERVAL }.forEach { it.dead = true }
            }
        }
    }
}

val cluster = Cluster()

val BLOCK_SIZE = 128L
val MAX_DEPTH_AT_SCALE = 6
val MAX_PATH_LENGTH = 900
val NAME_FORMAT = Regex("[A-Za-z0-9._-]{1,64}")

sealed class Node(var name: String, var parent: Dir?) {
    abstract val names: Long
    abstract val space: Long

    val path: String
        get() {
            val parts = ArrayList<String>()
            var current: Node = this
            while (true) {
                val up = current.parent ?: break
                parts += current.name
                current = up
            }
            return if (parts.isEmpty()) "/" else parts.asReversed().joinToString("/", prefix = "/")
        }

    val depth: Int
        get() = generateSequence(parent) { it.parent }.count()
}

class Dir(name: String, parent: Dir?) : Node(name, parent) {
    val children = LinkedHashMap<String, Node>()
    var nameQuota: Long? = null
    var spaceQuota: Long? = null
    override var names = 1L
    override var space = 0L
}

class FileNode(name: String, parent: Dir, val size: Long, val replication: Int) : Node(name, parent) {
    override val names = 1L
    override val space = size * replication
}

class Pool<T : Any> {
    private val items = ArrayList<T>()
    private val positions = HashMap<T, Int>()

    val size: Int get() = items.size

    fun add(item: T) {
        if (item in positions) return
        positions[item] = items.size
        items += item
    }

    fun remove(item: T) {
        val index = positions.remove(item) ?: return
        val last = items.removeAt(items.lastIndex)
        if (index < items.size) {
            items[index] = last
            positions[last] = index
        }
    }

    fun randomOrNull(): T? = if (items.isEmpty()) null else items[random.nextInt(items.size)]

    fun sample(tries: Int, accept: (T) -> Boolean): T? {
        repeat(tries) {
            val item = randomOrNull() ?: return null
            if (accept(item)) return item
        }
        return null
    }
}

class Namespace {
    val root = Dir("", null)
    val dirs = Pool<Dir>().also { it.add(root) }
    val files = Pool<FileNode>()

    fun parse(text: String): List<String>? {
        if (text == "/") return emptyList()
        if (!text.startsWith("/")) return null
        val parts = text.substring(1).split('/')
        return if (parts.all { NAME_FORMAT.matches(it) && it != "." && it != ".." }) parts else null
    }

    fun lookup(text: String): Node? {
        val parts = parse(text) ?: return null
        if (parts.isEmpty()) return root
        return parentOf(parts)?.children?.get(parts.last())
    }

    fun mkdir(text: String): Boolean {
        if (safeMode.on) return false
        val parts = parse(text)?.takeIf { it.isNotEmpty() } ?: return false
        val parent = parentOf(parts) ?: return false
        if (parts.last() in parent.children || !fits(chain(parent), 1, 0)) return false
        attach(Dir(parts.last(), parent), parent)
        return true
    }

    fun create(text: String, size: Long, replication: Int, writer: String?): Boolean {
        if (safeMode.on || writer != null && writer !in cluster.nodes) return false
        val parts = parse(text)?.takeIf { it.isNotEmpty() } ?: return false
        val parent = parentOf(parts) ?: return false
        val charge = (size + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE * replication
        if (parts.last() in parent.children || !fits(chain(parent), 1, charge)) return false
        attach(FileNode(parts.last(), parent, size, replication), parent)
        return true
    }

    fun remove(text: String): Boolean {
        if (safeMode.on) return false
        val node = lookup(text) ?: return false
        val parent = node.parent ?: return false
        parent.children.remove(node.name)
        chain(parent).forEach { it.names -= node.names; it.space -= node.space }
        forget(node)
        return true
    }

    fun move(source: String, target: String): Boolean {
        if (safeMode.on) return false
        val node = lookup(source) ?: return false
        val sourceParent = node.parent ?: return false
        val targetParts = parse(target)?.takeIf { it.isNotEmpty() } ?: return false
        val targetParent = parentOf(targetParts) ?: return false
        if (targetParts.last() in targetParent.children || target.startsWith("$source/")) return false
        val oldAncestors = chain(sourceParent)
        val newAncestors = chain(targetParent)
        val gaining = newAncestors.filter { it !in oldAncestors }
        if (!fits(gaining, node.names, node.space)) return false
        sourceParent.children.remove(node.name)
        oldAncestors.filter { it !in newAncestors }.forEach { it.names -= node.names; it.space -= node.space }
        node.name = targetParts.last()
        node.parent = targetParent
        targetParent.children[node.name] = node
        gaining.forEach { it.names += node.names; it.space += node.space }
        return true
    }

    fun quota(text: String, nameQuota: Long?, spaceQuota: Long?): Boolean {
        if (safeMode.on) return false
        val dir = lookup(text) as? Dir ?: return false
        dir.nameQuota = nameQuota
        dir.spaceQuota = spaceQuota
        return true
    }

    fun accepts(parent: Dir, names: Long, space: Long): Boolean = fits(chain(parent), names, space)

    private fun parentOf(parts: List<String>): Dir? {
        var current = root
        for (part in parts.dropLast(1)) current = current.children[part] as? Dir ?: return null
        return current
    }

    private fun chain(dir: Dir): List<Dir> = generateSequence(dir) { it.parent }.toList()

    private fun fits(directories: List<Dir>, names: Long, space: Long): Boolean = directories.all { dir ->
        val nameQuota = dir.nameQuota
        val spaceQuota = dir.spaceQuota
        (names <= 0 || nameQuota == null || dir.names + names <= nameQuota) &&
            (space <= 0 || spaceQuota == null || dir.space + space <= spaceQuota)
    }

    private fun attach(node: Node, parent: Dir) {
        parent.children[node.name] = node
        chain(parent).forEach { it.names += node.names; it.space += node.space }
        when (node) {
            is Dir -> dirs.add(node)
            is FileNode -> files.add(node)
        }
    }

    private fun forget(node: Node) {
        when (node) {
            is FileNode -> files.remove(node)
            is Dir -> {
                dirs.remove(node)
                node.children.values.forEach(::forget)
            }
        }
    }
}


val namespace = Namespace()

fun childPath(parent: Dir, name: String): String = if (parent.parent == null) "/$name" else "${parent.path}/$name"

val COMMON_NAMES = listOf("a", "b", "c", "data", "logs", "tmp", "x", "y", "app.log", "part-0", "src", "out", "v1", "img", "db", "z")
val RARE_NAMES = listOf("A", "B", "Data", "x.y", "a-b", "_", "-", "...", "a.b.c", "z9", "LOGS", "0", "file.txt", "Z", "q".repeat(64))
val TOP_LEVEL_NAMES = listOf("user", "data", "warehouse", "tmp", "archive", "apps")
val FILE_NAME_PATTERNS = listOf("part-%06d.parquet", "f%06d", "log.%d", "Report_%d.csv", "img%06d.JPG", "%d.bin")
val DIR_NAME_PATTERNS = listOf("dt_2026-10-%05d", "v%d", "p%05d", "stage-%d", "x%05d", "run.%d")
val UNKNOWN_COMMANDS = listOf(
    "tick", "Tick", "WAIT", "SLEEP", "stop", "Stop", "KILL", "start", "BOOT", "REBOOT", "restart", "SAFE", "SAFEMODE_ON",
    "Safemode", "HEARTBEAT", "BLOCKREPORT", "REPLICATE", "DECOMMISSION", "fsck", "Register", "STATUS", "HELP",
)
val NODE_NAME_STYLES = listOf(
    List(40) { "dn${it + 1}" },
    listOf("a", "b", "B", "dn1", "dn10", "dn2", "node_7", "Z-9", "x".repeat(32), "Ab", "aB", "n-1", "n_1", "N1", "z", "Q"),
    List(40) { "worker-%02d".format(it + 1) },
)
val RACK_NAME_STYLES = listOf(
    List(25) { "r${it + 1}" },
    listOf("rackA", "rackB", "R_c", "rk-1", "Z", "a", "rackAA", "rack"),
)
val BAD_CLUSTER_NAMES = listOf("1dn", "-", "dn.1", "_a", "x".repeat(33), "dn/1", "dé", "9", "-a", "a.b")
var nameCounter = 0
var createdFiles = 0
var askedQueries = 0

fun smallName(): String = weighted(
    { COMMON_NAMES[skewedIndex(COMMON_NAMES.size)] } to 40,
    { pick(RARE_NAMES) } to 10,
    { "n${++nameCounter}" } to 50,
)()

fun uniqueName(patterns: List<String>): String {
    nameCounter++
    return pick(patterns).format(nameCounter)
}

fun fileSize(): Long = if (largeScale) {
    weighted(
        { random.nextLong(1, 129) } to 45,
        { random.nextLong(129, 513) } to 33,
        { random.nextLong(513, 1025) } to 18,
        { random.nextLong(1025, 2561) } to 4,
    )()
} else {
    weighted(
        { pick(listOf(0L, 1L, 10L, 50L, 100L, 127L, 128L, 129L, 255L, 256L, 257L, 300L, 384L, 385L)) } to 55,
        { random.nextLong(0, 700) } to 37,
        { random.nextLong(700, 2_600) } to 8,
    )()
}

fun replication(): Int = if (largeScale) {
    weighted(3 to 70, 2 to 15, 1 to 10, 4 to 3, 5 to 1, 6 to 1)
} else {
    weighted(1 to 12, 2 to 25, 3 to 40, 4 to 8, 5 to 5, 6 to 4, 10 to 6)
}

fun capacity(): Long = if (largeScale) {
    if (chance(0.5)) 1_000_000L else random.nextLong(700_000, 1_300_001)
} else {
    weighted(
        { pick(listOf(127L, 128L, 129L, 256L, 300L)) } to 6,
        { random.nextLong(385, 2_000) } to 12,
        { 10_000L } to 32,
        { 20_000L } to 15,
        { random.nextLong(5_000, 50_000) } to 25,
        { 1_000_000_000L } to 10,
    )()
}

fun existingDir(): Dir = namespace.dirs.randomOrNull() ?: namespace.root

fun topLevelDir(): Dir? = namespace.root.children.values.filterIsInstance<Dir>().randomOrNull(random)

fun existingEntryPath(): String {
    val node: Node? = if (namespace.files.size > 0 && chance(0.6)) namespace.files.randomOrNull() else namespace.dirs.randomOrNull()
    return node?.path ?: "/"
}

fun missingPath(): String = childPath(existingDir(), "gone${random.nextInt(100)}")

fun brokenPath(): String = pick(listOf("data", "/data/", "//data", "/a/./b", "/.", "/..", "/a//b", "/a*b", "/é", "a/b"))

fun smallParent(): Dir {
    val candidates = List(4) { existingDir() }.filter { it.path.length < MAX_PATH_LENGTH - 70 }
    if (candidates.isEmpty()) return namespace.root
    val open = candidates.filter { namespace.accepts(it, names = 1, space = 4 * BLOCK_SIZE * 3) }
    return if (open.isNotEmpty() && chance(0.85)) open.first() else candidates.first()
}

fun scaleParent(): Dir {
    repeat(4) {
        val dir = namespace.dirs.sample(10) { it.depth in 1 until MAX_DEPTH_AT_SCALE } ?: return namespace.root
        if (namespace.accepts(dir, names = 1, space = 8 * BLOCK_SIZE * 3)) return dir
    }
    return existingDir()
}

fun writer(): String? = when {
    largeScale -> if (chance(0.6)) cluster.randomNode()?.name else null
    else -> weighted(
        { cluster.randomNode()?.name } to 45,
        { cluster.stopped.randomOrNull(random)?.name } to 10,
        { null } to 32,
        { "ghost${random.nextInt(5)}" } to 9,
        { pick(BAD_CLUSTER_NAMES) } to 4,
    )()
}

fun canPlaceReplicas(): Boolean = cluster.running.any { it.capacity >= 1_000 }

fun create() {
    if (!largeScale && !canPlaceReplicas()) return query()
    createdFiles++
    val path = when {
        largeScale -> childPath(scaleParent(), uniqueName(FILE_NAME_PATTERNS))
        else -> weighted(
            { childPath(smallParent(), smallName()) } to 86,
            { existingEntryPath() } to 6,
            { "/" } to 2,
            { "${missingPath()}/x" } to 3,
            { namespace.files.randomOrNull()?.let { "${it.path}/x" } ?: "/" } to 3,
        )()
    }
    val size = fileSize()
    val replication = replication()
    val writer = writer()
    emit(listOfNotNull("CREATE", path, size.toString(), replication.toString(), writer).joinToString(" "))
    if (writer !in BAD_CLUSTER_NAMES) namespace.create(path, size, replication, writer)
}

fun mkdir() {
    val path = if (largeScale) {
        childPath(namespace.dirs.sample(10) { it.depth in 1 until MAX_DEPTH_AT_SCALE } ?: (topLevelDir() ?: namespace.root), uniqueName(DIR_NAME_PATTERNS))
    } else {
        weighted({ childPath(smallParent(), smallName()) } to 85, { existingEntryPath() } to 8, { "/" } to 2, { brokenPath() } to 5)()
    }
    emit("MKDIR $path")
    namespace.mkdir(path)
}

fun remove() {
    val path = if (largeScale) {
        if (chance(0.97)) namespace.files.randomOrNull()?.path ?: "/none" else namespace.dirs.sample(10) { it.depth >= 4 }?.path ?: "/none"
    } else {
        weighted(
            { namespace.files.randomOrNull()?.path ?: missingPath() } to 65,
            { namespace.dirs.sample(4) { it.parent != null }?.path ?: missingPath() } to 18,
            { "/" } to 5,
            { missingPath() } to 6,
            { brokenPath() } to 6,
        )()
    }
    emit("RM $path")
    namespace.remove(path)
}

fun move() {
    val source = namespace.files.randomOrNull()?.takeIf { chance(0.7) } ?: namespace.dirs.sample(5) { it.depth >= 2 } ?: namespace.files.randomOrNull()
    if (source == null) {
        emit("MV /nothing /nowhere")
        return
    }
    val parent = if (largeScale) scaleParent() else smallParent()
    val name = if (largeScale) uniqueName(FILE_NAME_PATTERNS) else smallName()
    val target = if (source is Dir && chance(0.15)) childPath(source, "inside") else childPath(parent, name)
    emit("MV ${source.path} $target")
    namespace.move(source.path, target)
}

fun quota() {
    val dir = namespace.dirs.sample(5) { it.parent != null } ?: return emit("QUOTA / - -")
    val nameQuota = if (chance(0.3)) null else dir.names + random.nextLong(if (largeScale) 100 else 2, if (largeScale) 1_000 else 30)
    val spaceQuota = if (chance(0.3)) null else dir.space + BLOCK_SIZE * random.nextLong(if (largeScale) 500 else 2, if (largeScale) 5_000 else 60)
    emit("QUOTA ${dir.path} ${nameQuota ?: "-"} ${spaceQuota ?: "-"}")
    namespace.quota(dir.path, nameQuota, spaceQuota)
}

fun nodeName(): String = when {
    largeScale -> cluster.randomNode()?.name ?: "d1"
    else -> weighted(
        { cluster.randomNode()?.name ?: "dn1" } to 70,
        { cluster.stopped.randomOrNull(random)?.name ?: "dn1" } to 18,
        { "ghost${random.nextInt(5)}" } to 7,
        { pick(BAD_CLUSTER_NAMES) } to 5,
    )()
}

fun query() {
    askedQueries++
    val line = if (largeScale) {
        weighted(
            { "FSCK" } to 30,
            { "SAFEMODE" } to 20,
            { "NODE ${nodeName()}" } to 30,
            { "LOCATE ${namespace.files.randomOrNull()?.path ?: "/"}" } to 20,
        )()
    } else {
        weighted(
            { "FSCK" } to 16,
            { "SAFEMODE" } to 12,
            { "NODE ${nodeName()}" } to 22,
            { "LOCATE ${namespace.files.randomOrNull()?.path ?: existingEntryPath()}" } to 30,
            { "COUNT ${existingEntryPath()}" } to 6,
            { "LS ${namespace.dirs.sample(5) { it.children.size <= 10 }?.path ?: "/"}" } to 6,
            { "BLOCKS ${namespace.files.randomOrNull()?.path ?: "/"}" } to 4,
            { "LOCATE ${existingDir().path}" } to 4,
        )()
    }
    emit(line)
}

fun rejectedChange() {
    val ghost = "ghost${random.nextInt(5)}"
    val missing = missingPath()
    emit(
        weighted(
            { "MKDIR $missing/x" } to 16,
            { "RM /" } to 12,
            { "RM $missing" } to 10,
            { "CREATE /fresh${random.nextInt(100)} 10 1 $ghost" } to 14,
            { "CREATE /fresh${random.nextInt(100)} 10 1 -" } to 8,
            { "CREATE / 10 1" } to 8,
            { "MV $missing /moved${random.nextInt(100)}" } to 10,
            { "QUOTA $missing - -" } to 8,
            { "MKDIR ${brokenPath()}" } to 6,
            { "QUOTA /x 0 -" } to 5,
            { "MV / /x" } to 3,
        )(),
    )
}

fun timeTrouble() {
    val running = cluster.running.randomOrNull(random)
    val stopped = cluster.stopped.randomOrNull(random)
    emit(
        weighted(
            { stopped?.let { "STOP ${it.name}" } ?: "STOP ghost1" } to 16,
            { running?.let { "START ${it.name}" } ?: "START ghost2" } to 16,
            { "STOP ghost${random.nextInt(5)}" } to 8,
            { "START ${pick(BAD_CLUSTER_NAMES)}" } to 6,
            { "STOP" } to 4,
            { "START a b" } to 4,
            { "TICK ${pick(listOf("0", "-1", "01", "1000001", "x", "1.5", "99999999999999999999", "+3"))}" } to 16,
            { "TICK" } to 4,
            { "TICK 1 2" } to 4,
            { "RESTART now" } to 6,
            { "SAFEMODE on" } to 6,
            { "${pick(UNKNOWN_COMMANDS)} ${running?.name ?: "x"}" } to 12,
            { "\tTICK 1" } to 3,
            { "TICK 1\r" } to 3,
        )(),
    )
}

fun firstJudgmentAfter(time: Long): Long = (time + RECHECK_INTERVAL - 1) / RECHECK_INTERVAL * RECHECK_INTERVAL

fun deadlineOf(node: NodeSpec): Long = firstJudgmentAfter(node.stoppedAt + DEAD_INTERVAL + 1)

fun boundaryTargets(): List<Long> {
    val now = clock.now
    val targets = mutableListOf(now + 1, (now / 3 + 1) * 3, firstJudgmentAfter(now + 1))
    for (node in cluster.stopped) {
        targets += listOf(node.stoppedAt + STALE_INTERVAL - 1, node.stoppedAt + STALE_INTERVAL, node.stoppedAt + STALE_INTERVAL + 1)
        if (!node.dead) targets += listOf(deadlineOf(node) - 1, deadlineOf(node), deadlineOf(node) + 3)
    }
    safeMode.exitAt?.let { targets += listOf(it - 1, it, it + 2) }
    return targets.filter { it > now }
}

fun tickToBoundary() {
    cluster.tick(pick(boundaryTargets()) - clock.now)
}

fun buildEpisode() {
    repeat(random.nextInt(3, 9)) {
        when (weighted("create" to 40, "mkdir" to 10, "query" to 30, "remove" to 7, "move" to 5, "quota" to 4, "tick" to 4)) {
            "create" -> if (safeMode.on) rejectedChange() else create()
            "mkdir" -> if (safeMode.on) rejectedChange() else mkdir()
            "query" -> query()
            "remove" -> remove()
            "move" -> move()
            "quota" -> quota()
            else -> cluster.tick(random.nextLong(1, 10))
        }
    }
}

fun outageEpisode() {
    val spare = if (chance(0.9)) (cluster.running.size - 2).coerceAtLeast(0) else cluster.running.size
    val victims = cluster.running.shuffled(random).take(minOf(spare, if (chance(0.3)) 2 else 1))
    if (victims.isEmpty()) return idleEpisode()
    victims.forEach(cluster::stop)
    if (chance(0.5)) emit("NODE ${victims.first().name}")
    if (chance(0.4)) create()
    cluster.tick(STALE_INTERVAL - if (chance(0.5)) 1 else 0)
    if (chance(0.5)) cluster.tick(1)
    emit("NODE ${victims.first().name}")
    if (chance(0.6)) create()
    if (chance(0.5)) query()
    val deadline = deadlineOf(victims.first())
    if (chance(0.5) && deadline - 1 > clock.now) cluster.tick(deadline - 1 - clock.now)
    if (deadline > clock.now) cluster.tick(deadline - clock.now)
    repeat(random.nextInt(1, 4)) { query() }
    repeat(random.nextInt(0, 4)) { cluster.tick(pick(listOf(1L, 2L, 3L, 6L))) }
    if (chance(0.5)) query()
}

fun recoveryEpisode() {
    val returning = cluster.stopped.shuffled(random).take(if (chance(0.3)) 2 else 1)
    if (returning.isEmpty()) return timeTrouble()
    returning.forEach(cluster::start)
    repeat(random.nextInt(1, 4)) { query() }
}

fun flapEpisode() {
    val node = cluster.running.randomOrNull(random) ?: return idleEpisode()
    cluster.stop(node)
    if (chance(0.4)) emit("NODE ${node.name}")
    cluster.tick(pick(listOf(1L, 10L, 29L, 30L, 31L, random.nextLong(2, 40))))
    if (chance(0.5)) create()
    cluster.start(node)
    if (chance(0.5)) query()
}

fun restartEpisode() {
    if (chance(0.5)) cluster.running.randomOrNull(random)?.let(cluster::stop)
    if (chance(0.3)) cluster.tick(random.nextLong(1, 40))
    cluster.restart()
    emit("SAFEMODE")
    repeat(random.nextInt(1, 3)) { query() }
    repeat(random.nextInt(1, 4)) { rejectedChange() }
    if (chance(0.3)) {
        cluster.tick(random.nextLong(1, 25))
        cluster.restart()
        emit("SAFEMODE")
    }
    val stopped = cluster.stopped.shuffled(random)
    val returning = if (chance(0.8)) stopped else stopped.drop(1)
    for (node in returning) {
        if (chance(0.4)) cluster.tick(random.nextLong(1, 12))
        cluster.start(node)
    }
    emit("SAFEMODE")
    val exitAt = safeMode.exitAt
    if (exitAt != null && chance(0.6)) {
        if (exitAt - 1 > clock.now) cluster.tick(exitAt - 1 - clock.now)
        emit("SAFEMODE")
        if (chance(0.3)) rejectedChange()
        cluster.tick(1)
    } else {
        cluster.tick(random.nextLong(20, 60))
    }
    emit("SAFEMODE")
    if (chance(0.5)) query()
}

fun idleEpisode() {
    cluster.tick(
        weighted(
            { random.nextLong(1, 30) } to 50,
            { random.nextLong(30, 700) } to 35,
            { random.nextLong(700, 1_500) } to 13,
            { 1_000_000L } to 2,
        )(),
    )
}

fun smallBudgetLeft(): Boolean =
    createdFiles < options.files || askedQueries < options.queries || clock.ticked < options.seconds

fun runSmall() {
    val plan = planCluster()
    val earlyCount = if (chance(0.8)) plan.size else (plan.size * random.nextDouble(0.5, 1.0)).toInt().coerceAtLeast(1)
    plan.take(earlyCount).forEach(cluster::register)
    cluster.pending.addAll(plan.drop(earlyCount))
    repeat(2) { buildEpisode() }
    var episodes = 0
    while (smallBudgetLeft() && episodes < 400) {
        episodes++
        when (
            weighted(
                "build" to if (createdFiles < options.files) 34 else 8,
                "outage" to if (clock.ticked < options.seconds) 16 else 3,
                "recovery" to 12,
                "flap" to 8,
                "restart" to 6,
                "trouble" to 8,
                "register" to if (cluster.pending.isEmpty()) 0 else 6,
                "idle" to if (clock.ticked < options.seconds) 6 else 1,
                "query" to if (askedQueries < options.queries) 10 else 2,
            )
        ) {
            "build" -> buildEpisode()
            "outage" -> outageEpisode()
            "recovery" -> recoveryEpisode()
            "flap" -> flapEpisode()
            "restart" -> restartEpisode()
            "trouble" -> timeTrouble()
            "register" -> cluster.register(cluster.pending.removeFirst())
            "idle" -> if (chance(0.5)) tickToBoundary() else idleEpisode()
            else -> repeat(random.nextInt(1, 4)) { query() }
        }
    }
}

fun planCluster(): List<NodeSpec> {
    val names = if (largeScale) List(options.nodes) { "d${it + 1}" } else pick(NODE_NAME_STYLES).shuffled(random).take(options.nodes)
    val racks = if (largeScale) List(options.racks) { "r${it + 1}" } else pick(RACK_NAME_STYLES).shuffled(random).take(options.racks)
    return names.mapIndexed { index, name ->
        val rack = if (largeScale) racks[index % racks.size] else if (index < racks.size) racks[index] else pick(racks)
        NodeSpec(name, rack, capacity())
    }.shuffled(random)
}

fun scheduleReturn(node: NodeSpec) {
    node.returnAt = weighted(
        { clock.now + random.nextLong(5, 40) } to 20,
        { clock.now + random.nextLong(1_000, 5_000) } to 55,
        { null } to 25,
    )()
}

fun largeRestart() {
    cluster.restart()
    emit("SAFEMODE")
    repeat(3) { query() }
    repeat(3) { rejectedChange() }
    for (node in cluster.stopped.shuffled(random)) {
        if (chance(0.5)) cluster.tick(random.nextLong(1, 6))
        cluster.start(node)
        emit("SAFEMODE")
    }
    cluster.tick(SAFE_MODE_EXTENSION + random.nextLong(0, 3))
    emit("SAFEMODE")
    emit("FSCK")
}

fun runLarge() {
    planCluster().forEach(cluster::register)
    for (name in TOP_LEVEL_NAMES) {
        emit("MKDIR /$name")
        namespace.mkdir("/$name")
    }
    val restartPoints = ArrayDeque(listOf(0.22, 0.48, 0.71, 0.9).map { (it * options.files).toInt() })
    val stopsPlanned = options.nodes / 10
    var stopsDone = 0
    while (createdFiles < options.files || askedQueries < options.queries || clock.ticked < options.seconds) {
        val progress = createdFiles.toDouble() / options.files
        cluster.stopped.filter { node -> node.returnAt?.let { it <= clock.now } == true }.forEach(cluster::start)
        if (restartPoints.isNotEmpty() && createdFiles >= restartPoints.first()) {
            restartPoints.removeFirst()
            largeRestart()
            continue
        }
        if (stopsDone < stopsPlanned && progress > 0.05 && chance(stopsPlanned / (options.files * 0.9))) {
            cluster.running.shuffled(random).take(if (chance(0.25)) 2 else 1).forEach { node ->
                cluster.stop(node)
                scheduleReturn(node)
                stopsDone++
            }
            continue
        }
        val behindClock = clock.ticked < options.seconds * minOf(1.0, progress)
        when (
            weighted(
                "create" to if (createdFiles < options.files) 66 else 0,
                "query" to if (askedQueries < options.queries) 20 else 0,
                "tick" to if (behindClock || createdFiles >= options.files && clock.ticked < options.seconds) 12 else 1,
                "other" to 2,
            )
        ) {
            "create" -> create()
            "query" -> query()
            "tick" -> cluster.tick(random.nextLong(1, 16))
            else -> when (weighted("mkdir" to 40, "remove" to 40, "move" to 12, "quota" to 4, "trouble" to 4)) {
                "mkdir" -> mkdir()
                "remove" -> remove()
                "move" -> move()
                "quota" -> quota()
                else -> timeTrouble()
            }
        }
    }
}

if (largeScale) runLarge() else runSmall()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
