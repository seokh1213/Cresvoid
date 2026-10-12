const val BLOCK_SIZE = 128L

class Block(val id: Long, val size: Long, val replication: Int) {
    private val storedReplicas = mutableListOf<Replica>()

    val name: String get() = "blk_$id"

    val replicas: List<Replica> get() = storedReplicas

    val knownReplicas: List<Replica> get() = storedReplicas.filter { it.known }

    val knownReplicaCount: Int get() = storedReplicas.count { it.known }

    val knownLocations: List<DataNode> get() = knownReplicas.map { it.node }

    fun addReplica(replica: Replica) {
        storedReplicas += replica
    }

    fun removeReplica(replica: Replica) {
        storedReplicas -= replica
    }
}

fun blockSizesOf(fileSize: Long): List<Long> {
    val blockCount = ((fileSize + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt()
    val lastBlockSize = fileSize - BLOCK_SIZE * (blockCount - 1)
    return List(blockCount) { index -> if (index < blockCount - 1) BLOCK_SIZE else lastBlockSize }
}
