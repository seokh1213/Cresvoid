class Namespace {
    private val root = Directory(name = "", parent = null)
    private var lastBlockId = 0L

    fun makeDirectory(path: DfsPath) {
        val parent = findFreeSlot(path)
        verifyQuota(parent.pathToRoot, QuotaCharge(names = 1, space = 0))
        attach(Directory(path.lastName, parent), parent)
    }

    fun createFile(path: DfsPath, size: Long, replication: Int): File {
        val parent = findFreeSlot(path)
        val blockSizes = blockSizesOf(size)
        verifyQuota(parent.pathToRoot, QuotaCharge(names = 1, space = blockSizes.size * BLOCK_SIZE * replication))
        val blocks = blockSizes.map { blockSize -> Block(++lastBlockId, blockSize) }
        return File(path.lastName, parent, size, replication, blocks).also { attach(it, parent) }
    }

    fun remove(path: DfsPath): Entry {
        if (path.isRoot) reject(ErrorCode.ROOT)
        val parent = findParent(path)
        val entry = parent.child(path.lastName) ?: reject(ErrorCode.NOT_FOUND)
        parent.removeChild(entry)
        parent.pathToRoot.forEach { it.exclude(entry.usage) }
        return entry
    }

    fun move(source: DfsPath, target: DfsPath) {
        if (source.isRoot) reject(ErrorCode.ROOT)
        val sourceParent = findParent(source)
        val entry = sourceParent.child(source.lastName) ?: reject(ErrorCode.NOT_FOUND)
        if (target.isRoot) reject(ErrorCode.EXISTS)
        val targetParent = findParent(target)
        if (targetParent.child(target.lastName) != null) reject(ErrorCode.EXISTS)
        if (source.isAncestorOf(target)) reject(ErrorCode.SUBTREE)

        val oldAncestors = sourceParent.pathToRoot
        val newAncestors = targetParent.pathToRoot
        val gainingAncestors = newAncestors.filter { it !in oldAncestors }
        val losingAncestors = oldAncestors.filter { it !in newAncestors }
        verifyQuota(gainingAncestors, QuotaCharge(names = entry.usage.names, space = entry.usage.space))

        sourceParent.removeChild(entry)
        losingAncestors.forEach { it.exclude(entry.usage) }
        entry.relocate(targetParent, target.lastName)
        targetParent.addChild(entry)
        gainingAncestors.forEach { it.include(entry.usage) }
    }

    fun setQuota(path: DfsPath, nameQuota: Long?, spaceQuota: Long?) {
        val directory = when (val entry = find(path)) {
            is Directory -> entry
            is File -> reject(ErrorCode.NOT_DIR)
        }
        directory.nameQuota = nameQuota
        directory.spaceQuota = spaceQuota
    }

    fun find(path: DfsPath): Entry {
        if (path.isRoot) return root
        return findParent(path).child(path.lastName) ?: reject(ErrorCode.NOT_FOUND)
    }

    fun findFile(path: DfsPath): File = when (val entry = find(path)) {
        is File -> entry
        is Directory -> reject(ErrorCode.NOT_FILE)
    }

    private fun findFreeSlot(path: DfsPath): Directory {
        if (path.isRoot) reject(ErrorCode.EXISTS)
        val parent = findParent(path)
        if (parent.child(path.lastName) != null) reject(ErrorCode.EXISTS)
        return parent
    }

    private fun findParent(path: DfsPath): Directory = path.parentNames.fold(root, ::enterDirectory)

    private fun enterDirectory(directory: Directory, name: String): Directory = when (val entry = directory.child(name)) {
        null -> reject(ErrorCode.NOT_FOUND)
        is File -> reject(ErrorCode.NOT_DIR)
        is Directory -> entry
    }

    private fun attach(entry: Entry, parent: Directory) {
        parent.addChild(entry)
        parent.pathToRoot.forEach { it.include(entry.usage) }
    }
}
