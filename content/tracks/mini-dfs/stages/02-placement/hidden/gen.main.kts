import kotlin.random.Random

class Options(val nodes: Int, val racks: Int, val files: Int, val queries: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--nodes" to "8", "--racks" to "3", "--files" to "60", "--queries" to "40", "--seed" to "1")
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

class NodeSpec(val name: String, val rack: String, val capacity: Long)

class Cluster {
    val nodes = LinkedHashMap<String, NodeSpec>()
    val pending = ArrayDeque<NodeSpec>()

    fun register(spec: NodeSpec) {
        emit("REGISTER ${spec.name} ${spec.rack} ${spec.capacity}")
        if (spec.name !in nodes) nodes[spec.name] = spec
    }

    fun randomNode(): NodeSpec? = if (nodes.isEmpty()) null else nodes.values.elementAt(random.nextInt(nodes.size))
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
        val parts = parse(text)?.takeIf { it.isNotEmpty() } ?: return false
        val parent = parentOf(parts) ?: return false
        if (parts.last() in parent.children || !fits(chain(parent), 1, 0)) return false
        attach(Dir(parts.last(), parent), parent)
        return true
    }

    fun create(text: String, size: Long, replication: Int, writer: String?): Boolean {
        if (writer != null && writer !in cluster.nodes) return false
        val parts = parse(text)?.takeIf { it.isNotEmpty() } ?: return false
        val parent = parentOf(parts) ?: return false
        val charge = (size + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE * replication
        if (parts.last() in parent.children || !fits(chain(parent), 1, charge)) return false
        attach(FileNode(parts.last(), parent, size, replication), parent)
        return true
    }

    fun remove(text: String): Boolean {
        val node = lookup(text) ?: return false
        val parent = node.parent ?: return false
        parent.children.remove(node.name)
        chain(parent).forEach { it.names -= node.names; it.space -= node.space }
        forget(node)
        return true
    }

    fun move(source: String, target: String): Boolean {
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
val RARE_NAMES = listOf("A", "B", "Data", "x.y", "a-b", "_", "-", "...", "a.b.c", "z9", "LOGS", "0", "9z", "file.txt", "Z", "q".repeat(64))
val TOP_LEVEL_NAMES = listOf("user", "data", "warehouse", "tmp", "archive", "apps")
val FILE_NAME_PATTERNS = listOf("part-%06d.parquet", "f%06d", "log.%d", "Report_%d.csv", "img%06d.JPG", "%d.bin")
val DIR_NAME_PATTERNS = listOf("dt_2026-10-%05d", "v%d", "p%05d", "stage-%d", "x%05d", "run.%d")
val UNKNOWN_COMMANDS = listOf(
    "mkdir", "Register", "REG", "REGISTERNODE", "Locate", "LOCATION", "NODES", "fsck", "FSCK2", "DECOMMISSION",
    "BALANCE", "REPORT", "DATANODE", "PUT", "GET", "CAT", "LIST", "Node", "HELP", "QUIT",
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
    weighted(1 to 15, 2 to 22, 3 to 38, 4 to 8, 5 to 5, 6 to 4, 8 to 3, 10 to 5)
}

fun capacity(): Long = if (largeScale) {
    if (chance(0.5)) 1_000_000L else random.nextLong(700_000, 1_300_001)
} else {
    weighted(
        { random.nextLong(1, 128) } to 4,
        { pick(listOf(127L, 128L, 129L, 200L, 255L, 256L, 384L)) } to 8,
        { random.nextLong(385, 2_000) } to 16,
        { 10_000L } to 25,
        { 20_000L } to 13,
        { random.nextLong(5_000, 50_000) } to 24,
        { pick(listOf(1_000_000_000L, random.nextLong(100_000, 1_000_000_000))) } to 10,
    )()
}

fun existingDir(): Dir = namespace.dirs.randomOrNull() ?: namespace.root

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
        { cluster.randomNode()?.name } to 55,
        { null } to 32,
        { "ghost${random.nextInt(5)}" } to 10,
        { pick(BAD_CLUSTER_NAMES) } to 3,
    )()
}

fun create() {
    val path = when {
        largeScale -> if (chance(0.995)) childPath(scaleParent(), uniqueName(FILE_NAME_PATTERNS)) else missingPath() + "/x"
        else -> weighted(
            { childPath(smallParent(), smallName()) } to 85,
            { existingEntryPath() } to 6,
            { "/" } to 2,
            { "${missingPath()}/x" } to 4,
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

fun topLevelDir(): Dir? = namespace.root.children.values.filterIsInstance<Dir>().randomOrNull(random)

fun remove() {
    val path = if (largeScale) {
        if (chance(0.97)) namespace.files.randomOrNull()?.path ?: "/none" else namespace.dirs.sample(10) { it.depth >= 4 }?.path ?: "/none"
    } else {
        weighted(
            { namespace.files.randomOrNull()?.path ?: missingPath() } to 65,
            { namespace.dirs.sample(4) { it.parent != null }?.path ?: missingPath() } to 20,
            { "/" } to 4,
            { missingPath() } to 6,
            { brokenPath() } to 5,
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
    val target = if (source is Dir && chance(0.15)) childPath(source, "inside") else childPath(parent, if (largeScale) uniqueName(FILE_NAME_PATTERNS) else smallName())
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

fun locate() {
    val path = when {
        largeScale -> if (chance(0.995)) namespace.files.randomOrNull()?.path ?: "/" else existingDir().path
        else -> weighted(
            { namespace.files.randomOrNull()?.path ?: "/" } to 80,
            { existingDir().path } to 8,
            { missingPath() } to 5,
            { brokenPath() } to 4,
            { "/" } to 3,
        )()
    }
    emit("LOCATE $path")
}

fun showNode() {
    val name = when {
        largeScale -> cluster.randomNode()?.name ?: "d1"
        else -> weighted(
            { cluster.randomNode()?.name ?: "dn1" } to 85,
            { "ghost${random.nextInt(5)}" } to 9,
            { pick(BAD_CLUSTER_NAMES) } to 6,
        )()
    }
    emit("NODE $name")
}

fun fsck() {
    emit(if (largeScale || chance(0.9)) "FSCK" else pick(listOf("FSCK now", "FSCK /", "FSCK -v")))
}

fun namespaceQuery() {
    val path = existingEntryPath()
    emit(
        weighted(
            "COUNT $path" to 40,
            "LS ${namespace.dirs.sample(5) { it.children.size <= 12 }?.path ?: "/"}" to 25,
            "BLOCKS ${namespace.files.randomOrNull()?.path ?: path}" to 35,
        ),
    )
}

fun clusterTrouble() {
    val existing = cluster.randomNode()
    emit(
        weighted(
            { existing?.let { "REGISTER ${it.name} ${it.rack}x ${it.capacity + 1}" } ?: "REGISTER" } to 25,
            { "REGISTER ${pick(BAD_CLUSTER_NAMES)} r1 100" } to 15,
            { "REGISTER okname ${pick(BAD_CLUSTER_NAMES)} 100" } to 10,
            { "REGISTER fresh${random.nextInt(1000)} r1 ${pick(listOf("0", "1000000001", "01", "-5", "1e3", "99999999999999999999"))}" } to 15,
            { "REGISTER only r1" } to 5,
            { "REGISTER a b 1 2" } to 5,
            { "NODE" } to 5,
            { "NODE a b" } to 5,
            { "LOCATE" } to 5,
            { "LOCATE / /" } to 5,
            { "CREATE /fivearg 10 1 dn1 extra" } to 5,
        )(),
    )
}

fun malformedLine() {
    val path = existingEntryPath()
    emit(
        weighted(
            { "${pick(UNKNOWN_COMMANDS)} $path" } to 25,
            { pick(UNKNOWN_COMMANDS) } to 5,
            { "CREATE $path 10" } to 6,
            { "CREATE ${brokenPath()} 10 1" } to 6,
            { "CREATE $path ${pick(listOf("-1", "01", "1000001", "x"))} 1 dn1" } to 6,
            { "CREATE $path 10 ${pick(listOf("0", "11", "01"))} dn1" } to 6,
            { "MKDIR ${brokenPath()}" } to 6,
            { "MV $path" } to 4,
            { "RM" } to 3,
            { "QUOTA $path 0 -" } to 3,
            { "  LOCATE   ${namespace.files.randomOrNull()?.path ?: "/"}  " } to 6,
            { "" } to 4,
            { "\tFSCK" } to 3,
            { "FSCK\r" } to 3,
        )(),
    )
}

fun planCluster(): List<NodeSpec> {
    val names = if (largeScale) List(options.nodes) { "d${it + 1}" } else pick(NODE_NAME_STYLES).shuffled(random).take(options.nodes)
    val racks = if (largeScale) List(options.racks) { "r${it + 1}" } else pick(RACK_NAME_STYLES).shuffled(random).take(options.racks)
    return names.mapIndexed { index, name ->
        val rack = if (largeScale) racks[index % racks.size] else if (index < racks.size) racks[index] else pick(racks)
        NodeSpec(name, rack, capacity())
    }.shuffled(random)
}

enum class Operation { CREATE, MKDIR, REMOVE, MOVE, QUOTA, LOCATE, NODE, FSCK, NAMESPACE_QUERY, LATE_REGISTER, CLUSTER_TROUBLE, MALFORMED }

fun budgets(lateNodes: Int): MutableMap<Operation, Int> = if (largeScale) {
    mutableMapOf(
        Operation.CREATE to options.files,
        Operation.MKDIR to options.files / 20,
        Operation.REMOVE to options.files / 20,
        Operation.MOVE to options.files / 100,
        Operation.QUOTA to options.files / 200,
        Operation.LOCATE to options.queries / 2,
        Operation.NODE to options.queries / 5,
        Operation.FSCK to options.queries / 5,
        Operation.NAMESPACE_QUERY to options.queries / 10,
        Operation.LATE_REGISTER to lateNodes,
        Operation.CLUSTER_TROUBLE to options.queries / 200,
        Operation.MALFORMED to (options.files + options.queries) / 200,
    )
} else {
    mutableMapOf(
        Operation.CREATE to options.files,
        Operation.MKDIR to maxOf(3, options.files / 5),
        Operation.REMOVE to maxOf(3, options.files / 8),
        Operation.MOVE to maxOf(2, options.files / 12),
        Operation.QUOTA to maxOf(2, options.files / 15),
        Operation.LOCATE to maxOf(4, options.queries / 2),
        Operation.NODE to maxOf(3, options.queries / 4),
        Operation.FSCK to maxOf(3, options.queries / 6),
        Operation.NAMESPACE_QUERY to maxOf(3, options.queries / 6),
        Operation.LATE_REGISTER to lateNodes,
        Operation.CLUSTER_TROUBLE to maxOf(3, options.nodes / 3),
        Operation.MALFORMED to maxOf(4, (options.files + options.queries) / 14),
    )
}

fun nextOperation(remaining: Map<Operation, Int>, progress: Double): Operation {
    val weights = remaining.mapValues { (operation, count) ->
        val boost = if (progress < 0.2 && (operation == Operation.MKDIR || operation == Operation.CREATE)) 3 else 1
        count.toLong() * boost
    }
    var target = random.nextLong(weights.values.sum())
    for ((operation, weight) in weights) {
        if (target < weight) return operation
        target -= weight
    }
    error("남은 명령이 없다")
}

fun run() {
    val plan = planCluster()
    val earlyCount = when {
        largeScale -> plan.size - minOf(plan.size, 5)
        chance(0.2) -> 0
        else -> (plan.size * random.nextDouble(0.4, 1.0)).toInt().coerceAtLeast(1)
    }
    plan.take(earlyCount).forEach(cluster::register)
    cluster.pending.addAll(plan.drop(earlyCount))
    if (largeScale) {
        for (name in TOP_LEVEL_NAMES) {
            emit("MKDIR /$name")
            namespace.mkdir("/$name")
        }
    }
    val remaining = budgets(cluster.pending.size)
    val total = remaining.values.sum()
    var done = 0
    while (remaining.values.any { it > 0 }) {
        val operation = nextOperation(remaining, progress = done.toDouble() / total)
        remaining[operation] = remaining.getValue(operation) - 1
        done++
        when (operation) {
            Operation.CREATE -> create()
            Operation.MKDIR -> mkdir()
            Operation.REMOVE -> remove()
            Operation.MOVE -> move()
            Operation.QUOTA -> quota()
            Operation.LOCATE -> locate()
            Operation.NODE -> showNode()
            Operation.FSCK -> fsck()
            Operation.NAMESPACE_QUERY -> namespaceQuery()
            Operation.LATE_REGISTER -> cluster.register(cluster.pending.removeFirst())
            Operation.CLUSTER_TROUBLE -> clusterTrouble()
            Operation.MALFORMED -> malformedLine()
        }
    }
}

run()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
