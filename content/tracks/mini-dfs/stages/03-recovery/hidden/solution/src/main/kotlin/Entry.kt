import java.util.TreeMap

data class Usage(val directories: Long, val files: Long, val size: Long, val space: Long) {
    val names: Long get() = directories + files

    operator fun plus(other: Usage) =
        Usage(directories + other.directories, files + other.files, size + other.size, space + other.space)

    operator fun minus(other: Usage) =
        Usage(directories - other.directories, files - other.files, size - other.size, space - other.space)
}

sealed class Entry(name: String, parent: Directory?) {
    var name: String = name
        private set
    var parent: Directory? = parent
        private set

    abstract val usage: Usage

    val path: String
        get() = generateSequence(this) { it.parent }
            .map { it.name }
            .toList()
            .asReversed()
            .drop(1)
            .joinToString("/", prefix = "/")

    fun relocate(newParent: Directory, newName: String) {
        parent = newParent
        name = newName
    }

    fun files(): Sequence<File> = when (this) {
        is File -> sequenceOf(this)
        is Directory -> children.asSequence().flatMap { it.files() }
    }
}

class Directory(name: String, parent: Directory?) : Entry(name, parent) {
    private val childrenByName = TreeMap<String, Entry>()

    var nameQuota: Long? = null
    var spaceQuota: Long? = null

    override var usage = Usage(directories = 1, files = 0, size = 0, space = 0)
        private set

    val children: Collection<Entry> get() = childrenByName.values

    val pathToRoot: List<Directory> get() = generateSequence(this) { it.parent }.toList()

    fun child(name: String): Entry? = childrenByName[name]

    fun addChild(entry: Entry) {
        childrenByName[entry.name] = entry
    }

    fun removeChild(entry: Entry) {
        childrenByName.remove(entry.name)
    }

    fun include(change: Usage) {
        usage += change
    }

    fun exclude(change: Usage) {
        usage -= change
    }

    fun nameQuotaExceededBy(extraNames: Long): Boolean = nameQuota.let { it != null && usage.names + extraNames > it }

    fun spaceQuotaExceededBy(extraSpace: Long): Boolean = spaceQuota.let { it != null && usage.space + extraSpace > it }
}

class File(
    name: String,
    parent: Directory,
    val size: Long,
    val replication: Int,
    val blocks: List<Block>,
) : Entry(name, parent) {
    override val usage = Usage(directories = 0, files = 1, size = size, space = size * replication)
}
