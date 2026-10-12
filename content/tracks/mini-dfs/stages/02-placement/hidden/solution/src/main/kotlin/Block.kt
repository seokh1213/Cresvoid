const val BLOCK_SIZE = 128L

class Block(val id: Long, val size: Long, val replication: Int) {
    private val placedLocations = mutableListOf<DataNode>()

    val name: String get() = "blk_$id"

    val locations: List<DataNode> get() = placedLocations

    fun addLocation(node: DataNode) {
        placedLocations += node
    }
}

fun blockSizesOf(fileSize: Long): List<Long> {
    val blockCount = ((fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
    val lastBlockSize = fileSize - BLOCK_SIZE * (blockCount - 1)
    return List(blockCount) { index -> if (index < blockCount - 1) BLOCK_SIZE else lastBlockSize }
}
