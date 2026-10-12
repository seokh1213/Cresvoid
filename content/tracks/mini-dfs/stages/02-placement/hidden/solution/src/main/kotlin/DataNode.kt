class DataNode(val name: String, val rack: String, val capacity: Long) {
    var used: Long = 0
        private set
    var replicaCount: Int = 0
        private set

    val remaining: Long get() = capacity - used

    fun addReplica(block: Block) {
        used += block.size
        replicaCount++
    }

    fun removeReplica(block: Block) {
        used -= block.size
        replicaCount--
    }
}
