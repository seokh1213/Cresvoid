const val BLOCK_SIZE = 128L

class Block(val id: Long, val size: Long) {
    val name: String get() = "blk_$id"
}

fun blockSizesOf(fileSize: Long): List<Long> {
    val blockCount = ((fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
    val lastBlockSize = fileSize - BLOCK_SIZE * (blockCount - 1)
    return List(blockCount) { index -> if (index < blockCount - 1) BLOCK_SIZE else lastBlockSize }
}
