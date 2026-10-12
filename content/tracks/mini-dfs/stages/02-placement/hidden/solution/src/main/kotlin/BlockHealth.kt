class BlockHealth {
    var blocks: Int = 0
        private set
    var replicas: Long = 0
        private set
    var underReplicated: Int = 0
        private set
    var missing: Int = 0
        private set

    fun track(block: Block) {
        blocks++
        replicas += block.locations.size
        when {
            block.locations.isEmpty() -> missing++
            block.locations.size < block.replication -> underReplicated++
        }
    }

    fun untrack(block: Block) {
        blocks--
        replicas -= block.locations.size
        when {
            block.locations.isEmpty() -> missing--
            block.locations.size < block.replication -> underReplicated--
        }
    }
}
