import kotlin.random.Random

class Options(val dirs: Int, val files: Int, val counts: Int, val seed: Long)

fun parseOptions(arguments: Array<String>): Options {
    val values = mutableMapOf("--dirs" to "30", "--files" to "120", "--counts" to "40", "--seed" to "1")
    require(arguments.size % 2 == 0) { "옵션은 --이름 값 쌍으로 준다: ${arguments.joinToString(" ")}" }
    for ((name, value) in arguments.toList().chunked(2)) {
        require(name in values) { "모르는 옵션: $name" }
        values[name] = value
    }
    return Options(
        dirs = values.getValue("--dirs").toInt(),
        files = values.getValue("--files").toInt(),
        counts = values.getValue("--counts").toInt(),
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

val BLOCK_SIZE = 128L
val MAX_DEPTH_AT_SCALE = 8
val MAX_PATH_LENGTH = 900
val HUGE_QUOTA = 1_000_000_000_000L
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
    val quotaDirs = Pool<Dir>()

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

    fun create(text: String, size: Long, replication: Int): Boolean {
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
        if (nameQuota == null && spaceQuota == null) quotaDirs.remove(dir) else quotaDirs.add(dir)
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
                quotaDirs.remove(node)
                node.children.values.forEach(::forget)
            }
        }
    }
}

val namespace = Namespace()

fun childPath(parent: Dir, name: String): String = if (parent.parent == null) "/$name" else "${parent.path}/$name"

val COMMON_NAMES = listOf("a", "b", "c", "data", "logs", "tmp", "x", "y", "app.log", "part-0", "src", "out", "v1", "img", "db", "z")
val RARE_NAMES = listOf(
    "A", "B", "Data", "x.y", "a-b", "_", "-", "...", "a.b.c", "z9", "LOGS", "0", "9z", "-.-", "a_b",
    "file.txt", "Z", "q".repeat(64), "..a", "a..",
)
val TOP_LEVEL_NAMES = listOf("user", "data", "warehouse", "tmp", "archive", "apps", "logs", "shared")
val DEEP_NAME_PATTERNS = listOf("dt_2026-10-%05d", "v%d", "part%05d", "stage-%d", "x%05d", "Q%d", "run.%d", "_tmp%d")
val FILE_NAME_PATTERNS = listOf("part-%06d.parquet", "f%06d", "log.%d", "Report_%d.csv", "img%06d.JPG", "%d.bin", "_SUCCESS%d")
val UNKNOWN_COMMANDS = listOf(
    "mkdir", "Mkdir", "MKDIRS", "LIST", "DELETE", "RMDIR", "CAT", "PUT", "GET", "MOVE", "CP", "QUOTAS", "LSR",
    "COUNTS", "BLOCK", "STAT", "TOUCH", "ls", "Create", "rm", "HELP", "EXIT", "Mv", "count", "blocks", "Quota",
)
var nameCounter = 0

fun smallName(): String = weighted(
    { COMMON_NAMES[skewedIndex(COMMON_NAMES.size)] } to 45,
    { pick(RARE_NAMES) } to 15,
    { "n${++nameCounter}" } to 40,
)()

fun uniqueName(patterns: List<String>): String {
    nameCounter++
    return pick(patterns).format(nameCounter)
}

fun fileSize(): Long = if (largeScale) {
    weighted(
        { random.nextLong(1, 129) } to 45,
        { random.nextLong(129, 513) } to 30,
        { random.nextLong(513, 1025) } to 17,
        { random.nextLong(1025, 4097) } to 5,
        { pick(listOf(0L, 128L, 256L, 384L, 129L, 127L)) } to 3,
    )()
} else {
    weighted(
        { pick(listOf(0L, 1L, 127L, 128L, 129L, 255L, 256L, 257L, 300L, 383L, 384L, 385L)) } to 50,
        { random.nextLong(0, 2_000) } to 35,
        { pick(listOf(1_000_000L, 999_999L, 999_872L)) } to 1,
        { pick(listOf(4_096L, 65L, 640L, 641L)) } to 4,
        { random.nextLong(2_000, 20_000) } to 10,
    )()
}

fun replication(): Int = weighted(1 to 25, 2 to 20, 3 to 40, 4 to 5, 5 to 4, 7 to 2, 10 to 4)

fun nameQuotaFor(dir: Dir): Long? = weighted(
    { null } to 22,
    { dir.names + random.nextLong(4, 30) } to 34,
    { dir.names } to 7,
    { dir.names + 1 } to 10,
    { dir.names + 2 } to 8,
    { maxOf(1L, dir.names - 1) } to 5,
    { 1L } to 5,
    { HUGE_QUOTA } to 5,
    { random.nextLong(1, 1_000) } to 4,
)()

fun spaceQuotaFor(dir: Dir): Long? = weighted(
    { null } to 22,
    { dir.space + BLOCK_SIZE * random.nextLong(8, 80) } to 30,
    { 0L } to 4,
    { dir.space } to 6,
    { dir.space + BLOCK_SIZE * random.nextLong(1, 7) } to 10,
    { dir.space + BLOCK_SIZE * random.nextLong(1, 4) * replication() } to 8,
    { dir.space + BLOCK_SIZE * random.nextLong(1, 4) * replication() - 1 } to 5,
    { dir.space + random.nextLong(0, 2_000) } to 8,
    { HUGE_QUOTA } to 4,
    { maxOf(0L, dir.space - random.nextLong(1, 500)) } to 3,
)()

fun quotaText(value: Long?): String = value?.toString() ?: "-"

fun existingDir(): Dir = namespace.dirs.randomOrNull() ?: namespace.root

fun topLevelDir(except: Dir? = null): Dir? =
    namespace.root.children.values.filterIsInstance<Dir>().filter { it !== except }.randomOrNull(random)

fun existingEntryPath(): String {
    val node: Node? = if (namespace.files.size > 0 && chance(0.55)) namespace.files.randomOrNull() else namespace.dirs.randomOrNull()
    return node?.path ?: "/"
}

fun missingPath(): String {
    val missing = childPath(existingDir(), "${if (chance(0.5)) "no" else "gone"}${random.nextInt(100)}")
    return if (chance(0.5)) missing else "$missing/${smallName()}"
}

fun pathThroughFile(): String = namespace.files.randomOrNull()?.let { "${it.path}/${smallName()}" } ?: missingPath()

fun brokenPath(): String = pick(
    listOf(
        "data", "/data/", "//data", "/a/./b", "/a/../b", "/.", "/..", "/a//b", "/" + "x".repeat(65), "/a*b",
        "/a:b", "/é", "/한글", "/a\tb", "a/b", "./a", "/a/b/", "~", "/a\\b", "/a?",
    ),
)

fun deepPath(): String = List(random.nextInt(9, 31)) { pick(COMMON_NAMES) }.joinToString("/", prefix = "/")

fun smallParent(): Dir {
    val candidates = List(4) { existingDir() }.filter { it.path.length < MAX_PATH_LENGTH - 70 }
    if (candidates.isEmpty()) return namespace.root
    val open = candidates.filter { namespace.accepts(it, names = 1, space = 4 * BLOCK_SIZE * 3) }
    val choices = if (open.isNotEmpty() && chance(0.8)) open else candidates
    return if (chance(0.3)) choices.maxBy { it.depth } else choices.first()
}

fun smallNewPath(): String = weighted(
    { childPath(smallParent(), smallName()) } to 78,
    { existingEntryPath() } to 6,
    { missingPath() } to 5,
    { pathThroughFile() } to 4,
    { "/" } to 2,
    { deepPath() } to 2,
    { brokenPath() } to 3,
)()

fun failingScalePath(): String = weighted(
    { existingEntryPath() } to 30,
    { "${missingPath()}/x" } to 30,
    { brokenPath() } to 30,
    { "/" } to 10,
)()

fun scaleDirectoryParent(): Dir {
    val deep = namespace.dirs.sample(20) { it.depth in 2 until MAX_DEPTH_AT_SCALE }
    if (deep == null || chance(0.12)) return topLevelDir() ?: namespace.root
    val sibling = namespace.dirs.sample(20) { it.depth in 2 until MAX_DEPTH_AT_SCALE }
    val shallower = if (sibling != null && sibling.depth < deep.depth) sibling else deep
    return if (namespace.accepts(shallower, names = 1, space = 0) || chance(0.05)) shallower else topLevelDir() ?: namespace.root
}

fun scaleFileParent(): Dir {
    var choice = existingDir()
    repeat(6) {
        val quotaDir = namespace.quotaDirs.sample(10) { it.depth >= 2 }
        choice = (if (quotaDir != null && chance(0.6)) quotaDir else namespace.dirs.sample(20) { it.depth >= 2 }) ?: existingDir()
        if (chance(0.04) || namespace.accepts(choice, names = 1, space = 8 * BLOCK_SIZE * 3)) return choice
    }
    return choice
}

fun mkdir() {
    if (!largeScale || chance(0.015)) {
        val path = if (largeScale) failingScalePath() else smallNewPath()
        emit("MKDIR $path")
        namespace.mkdir(path)
        return
    }
    val parent = scaleDirectoryParent()
    val name = if (parent.depth == 1) "${parent.name.first()}%05d".format(++nameCounter) else uniqueName(DEEP_NAME_PATTERNS)
    val path = childPath(parent, name)
    emit("MKDIR $path")
    if (namespace.mkdir(path) && parent.depth == 1 && chance(0.9)) setScaleQuota(namespace.lookup(path) as Dir)
}

fun setScaleQuota(dir: Dir) {
    val expectedNames = (options.files + options.dirs).toLong() * 10 / maxOf(1, options.dirs)
    val tight = chance(0.08)
    val nameQuota = if (tight) expectedNames * random.nextLong(4, 9) / 10 else expectedNames * random.nextLong(40, 120) / 10
    val spaceQuota = if (tight) expectedNames * 600 * random.nextLong(4, 9) / 10 else expectedNames * 1_000 * random.nextLong(40, 120) / 10
    emit("QUOTA ${dir.path} $nameQuota $spaceQuota")
    namespace.quota(dir.path, nameQuota, spaceQuota)
}

fun create() {
    val path = when {
        !largeScale -> smallNewPath()
        chance(0.99) -> childPath(scaleFileParent(), uniqueName(FILE_NAME_PATTERNS))
        else -> failingScalePath()
    }
    val size = fileSize()
    val replication = replication()
    emit("CREATE $path $size $replication")
    namespace.create(path, size, replication)
}

fun remove() {
    val path = if (largeScale) {
        weighted(
            { namespace.files.randomOrNull()?.path ?: "/nothing" } to 85,
            { namespace.dirs.sample(20) { it.depth >= 4 }?.path ?: "/nothing" } to 11,
            { namespace.dirs.sample(20) { it.depth in 2..3 }?.path ?: "/nothing" } to 3,
            { missingPath() } to 1,
        )()
    } else {
        weighted(
            { namespace.files.randomOrNull()?.path ?: missingPath() } to 58,
            { namespace.dirs.sample(4) { it.parent != null }?.path ?: missingPath() } to 24,
            { "/" } to 3,
            { missingPath() } to 7,
            { pathThroughFile() } to 3,
            { brokenPath() } to 5,
        )()
    }
    emit("RM $path")
    namespace.remove(path)
}

fun move() {
    val (source, target) = if (largeScale) scaleMove() else smallMove()
    emit("MV $source $target")
    namespace.move(source, target)
}

fun scaleMove(): Pair<String, String> {
    val nothing = "/nothing" to "/nowhere"
    return weighted(
        {
            namespace.files.randomOrNull()?.let { file ->
                val parent = if (chance(0.5)) file.parent!! else scaleFileParent()
                file.path to childPath(parent, uniqueName(FILE_NAME_PATTERNS))
            } ?: nothing
        } to 50,
        {
            val dir = namespace.dirs.sample(20) { it.depth in 3..6 }
            val target = dir?.let { source -> namespace.dirs.sample(20) { it.depth == source.depth - 1 && it !== source.parent } }
            if (dir == null || target == null) nothing else dir.path to childPath(target, dir.name)
        } to 30,
        {
            val dir = namespace.dirs.sample(20) { it.depth == 2 }
            val top = dir?.let { source -> topLevelDir(except = source.parent) }
            if (dir == null || top == null) nothing else dir.path to childPath(top, dir.name)
        } to 20,
    )()
}

fun smallMove(): Pair<String, String> {
    val source = weighted(
        { existingEntryPath() } to 90,
        { "/" } to 2,
        { missingPath() } to 4,
        { pathThroughFile() } to 2,
        { brokenPath() } to 2,
    )()
    val sourceNode = namespace.lookup(source)
    val target = weighted(
        { childPath(smallParent(), smallName()) } to 55,
        { childPath(sourceNode?.parent ?: namespace.root, smallName()) } to 15,
        { existingEntryPath() } to 6,
        { if (sourceNode is Dir) childPath(sourceNode, smallName()) else source } to 7,
        { if (sourceNode is Dir) "$source/${smallName()}/${smallName()}" else missingPath() } to 2,
        { "/" } to 3,
        { missingPath() } to 4,
        { pathThroughFile() } to 4,
        { brokenPath() } to 4,
    )()
    return source to target
}

fun quota() {
    if (largeScale) {
        val dir = (if (chance(0.7)) namespace.quotaDirs.sample(10) { it.depth >= 2 } else null)
            ?: namespace.dirs.sample(20) { it.depth >= 2 }
            ?: existingDir()
        val tight = chance(0.15)
        val nameQuota = dir.names + if (tight) random.nextLong(0, 8) else random.nextLong(200, 2_000)
        val spaceQuota = dir.space + BLOCK_SIZE * if (tight) random.nextLong(0, 12) else random.nextLong(2_000, 20_000)
        emit("QUOTA ${dir.path} $nameQuota $spaceQuota")
        namespace.quota(dir.path, nameQuota, spaceQuota)
        return
    }
    val dir = namespace.dirs.sample(3) { it.parent != null } ?: namespace.root
    when (weighted("dir" to 76, "root" to 6, "file" to 6, "missing" to 4, "badNumber" to 8)) {
        "root" -> {
            val nameQuota = if (chance(0.5)) null else namespace.root.names + random.nextLong(10, 60)
            val spaceQuota = if (chance(0.5)) null else namespace.root.space + BLOCK_SIZE * random.nextLong(30, 300)
            emit("QUOTA / ${quotaText(nameQuota)} ${quotaText(spaceQuota)}")
            namespace.quota("/", nameQuota, spaceQuota)
        }
        "dir" -> {
            val nameQuota = nameQuotaFor(dir)
            val spaceQuota = spaceQuotaFor(dir)
            emit("QUOTA ${dir.path} ${quotaText(nameQuota)} ${quotaText(spaceQuota)}")
            namespace.quota(dir.path, nameQuota, spaceQuota)
        }
        "file" -> emit("QUOTA ${namespace.files.randomOrNull()?.path ?: "/f"} 5 -")
        "missing" -> emit("QUOTA ${missingPath()} 3 1000")
        else -> {
            val values = pick(
                listOf("0 -", "- -1", "01 5", "1000000000001 -", "- 1000000000001", "1.5 -", "-- -", "+3 -", "3 x", "- 99999999999999999999"),
            )
            emit("QUOTA ${dir.path} $values")
        }
    }
}

fun listEntries() {
    val path = if (largeScale) {
        namespace.dirs.sample(20) { it.children.size <= 12 }?.path ?: existingEntryPath()
    } else {
        weighted({ existingEntryPath() } to 80, { "/" } to 8, { missingPath() } to 6, { pathThroughFile() } to 3, { brokenPath() } to 3)()
    }
    emit("LS $path")
}

fun count() {
    val path = if (largeScale) {
        weighted(
            { "/" } to 30,
            { topLevelDir()?.path ?: "/" } to 25,
            { namespace.quotaDirs.randomOrNull()?.path ?: "/" } to 20,
            { existingDir().path } to 15,
            { namespace.files.randomOrNull()?.path ?: "/" } to 9,
            { missingPath() } to 1,
        )()
    } else {
        weighted({ existingEntryPath() } to 70, { "/" } to 15, { missingPath() } to 6, { pathThroughFile() } to 4, { brokenPath() } to 5)()
    }
    emit("COUNT $path")
}

fun showBlocks() {
    val path = if (largeScale) {
        namespace.files.randomOrNull()?.path ?: "/"
    } else {
        weighted(
            { (namespace.files.sample(3) { it.size <= 20_000 } ?: namespace.files.randomOrNull())?.path ?: "/" } to 70,
            { existingDir().path } to 12,
            { missingPath() } to 8,
            { pathThroughFile() } to 4,
            { brokenPath() } to 6,
        )()
    }
    emit("BLOCKS $path")
}

fun malformedLine() {
    val path = existingEntryPath()
    val badSize = pick(listOf("-1", "01", "+5", "1.5", "1e3", "0x10", "1000001", "99999999999999999999", "x"))
    val badReplication = pick(listOf("0", "11", "-", "01", "3.0", "-3"))
    emit(
        weighted(
            { "${pick(UNKNOWN_COMMANDS)} $path" } to 18,
            { pick(UNKNOWN_COMMANDS) } to 3,
            { "\tMKDIR /tab" } to 2,
            { "LS $path\r" } to 2,
            { "\r" } to 1,
            { "MKDIR" } to 3,
            { "MKDIR $path $path" } to 3,
            { "CREATE $path 1" } to 3,
            { "CREATE $path" } to 2,
            { "CREATE $path 1 1 extra more" } to 2,
            { "CREATE $path $badSize 1" } to 8,
            { "CREATE $path 10 $badReplication" } to 6,
            { "CREATE ${brokenPath()} 10 1" } to 5,
            { "MV $path" } to 3,
            { "MV $path ${brokenPath()}" } to 3,
            { "MV ${brokenPath()} /x" } to 2,
            { "RM" } to 2,
            { "RM $path $path" } to 2,
            { "QUOTA $path 1" } to 2,
            { "QUOTA $path 1 2 3" } to 2,
            { "LS" } to 2,
            { "LS $path /" } to 2,
            { "COUNT" } to 2,
            { "BLOCKS $path $path" } to 2,
            { "BLOCKS" } to 1,
            { "MKDIR ${brokenPath()}" } to 6,
            { "COUNT ${brokenPath()}" } to 3,
            { "   MKDIR    ${childPath(existingDir(), smallName())}   " } to 4,
            { "" } to 3,
            { "     " } to 2,
            { "  \t  " } to 1,
        )(),
    )
}

enum class Operation { MKDIR, CREATE, REMOVE, MOVE, QUOTA, LIST, COUNT, BLOCKS, MALFORMED }

fun budgets(): MutableMap<Operation, Int> {
    val base = options.dirs + options.files + options.counts
    return if (largeScale) {
        mutableMapOf(
            Operation.MKDIR to options.dirs,
            Operation.CREATE to options.files,
            Operation.COUNT to options.counts,
            Operation.LIST to options.counts / 25,
            Operation.BLOCKS to options.counts / 16,
            Operation.MOVE to options.files / 100,
            Operation.REMOVE to options.files / 100,
            Operation.QUOTA to options.dirs / 10,
            Operation.MALFORMED to base / 100,
        )
    } else {
        mutableMapOf(
            Operation.MKDIR to options.dirs,
            Operation.CREATE to options.files,
            Operation.COUNT to options.counts,
            Operation.LIST to maxOf(3, options.counts / 3),
            Operation.BLOCKS to maxOf(3, options.counts / 3),
            Operation.MOVE to maxOf(4, options.files / 6),
            Operation.REMOVE to maxOf(4, options.files / 8),
            Operation.QUOTA to maxOf(4, options.dirs / 3),
            Operation.MALFORMED to maxOf(6, base / 10),
        )
    }
}

fun nextOperation(remaining: Map<Operation, Int>, progress: Double): Operation {
    val weights = remaining.mapValues { (operation, count) ->
        val boost = if (operation == Operation.MKDIR && progress < 0.25) 4 else 1
        count.toLong() * boost
    }
    var target = random.nextLong(weights.values.sum())
    for ((operation, weight) in weights) {
        if (target < weight) return operation
        target -= weight
    }
    error("남은 명령이 없다")
}

fun prepareScaleLayout() {
    for (name in TOP_LEVEL_NAMES) {
        emit("MKDIR /$name")
        namespace.mkdir("/$name")
    }
    emit("QUOTA / $HUGE_QUOTA $HUGE_QUOTA")
    namespace.quota("/", HUGE_QUOTA, HUGE_QUOTA)
    for (name in listOf("user", "data", "warehouse")) {
        emit("QUOTA /$name ${HUGE_QUOTA - 1} ${HUGE_QUOTA - 1}")
        namespace.quota("/$name", HUGE_QUOTA - 1, HUGE_QUOTA - 1)
    }
}

fun run() {
    if (largeScale) prepareScaleLayout()
    val remaining = budgets()
    val total = remaining.values.sum()
    var done = 0
    while (remaining.values.any { it > 0 }) {
        val operation = nextOperation(remaining, done.toDouble() / total)
        remaining[operation] = remaining.getValue(operation) - 1
        done++
        when (operation) {
            Operation.MKDIR -> mkdir()
            Operation.CREATE -> create()
            Operation.REMOVE -> remove()
            Operation.MOVE -> move()
            Operation.QUOTA -> quota()
            Operation.LIST -> listEntries()
            Operation.COUNT -> count()
            Operation.BLOCKS -> showBlocks()
            Operation.MALFORMED -> malformedLine()
        }
    }
}

run()
System.out.write(output.toString().encodeToByteArray())
System.out.flush()
