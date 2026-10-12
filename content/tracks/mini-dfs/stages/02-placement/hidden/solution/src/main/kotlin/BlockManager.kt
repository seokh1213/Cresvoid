class BlockManager(private val cluster: Cluster) {
    private val placement = BlockPlacement(cluster)

    val health = BlockHealth()

    fun placeNewBlocks(blocks: List<Block>, writer: DataNode?) {
        for (block in blocks) {
            placeReplicas(block, writer)
            health.track(block)
        }
    }

    fun removeBlocks(blocks: Sequence<Block>) {
        for (block in blocks) {
            health.untrack(block)
            block.locations.forEach { node -> cluster.removeReplica(node, block) }
        }
    }

    private fun placeReplicas(block: Block, writer: DataNode?) {
        while (block.locations.size < block.replication) {
            val target = placement.chooseTarget(block, writer) ?: return
            block.addLocation(target)
            cluster.addReplica(target, block)
        }
    }
}
