class BlockPlacement(private val cluster: Cluster) {
    fun chooseTarget(block: Block, writer: DataNode?): DataNode? {
        val placed = block.locations
        val candidates = Candidates(block.replication, placed, cluster)
        return when (placed.size) {
            0 -> chooseFirstTarget(candidates, writer)
            1 -> candidates.bestOutsideRack(placed[0].rack) ?: candidates.best()
            2 -> chooseThirdTarget(candidates, first = placed[0], second = placed[1]) ?: candidates.best()
            else -> candidates.best()
        }
    }

    private fun chooseFirstTarget(candidates: Candidates, writer: DataNode?): DataNode? = when {
        writer == null -> candidates.best()
        candidates.accepts(writer) -> writer
        else -> candidates.bestInRack(writer.rack) ?: candidates.best()
    }

    private fun chooseThirdTarget(candidates: Candidates, first: DataNode, second: DataNode): DataNode? =
        if (first.rack != second.rack) candidates.bestInRack(second.rack) else null
}

private class Candidates(replication: Int, private val holders: List<DataNode>, private val cluster: Cluster) {
    private val maxReplicasPerRack = maxReplicasPerRack(replication, cluster.nodeCount, cluster.rackCount)

    fun accepts(node: DataNode): Boolean =
        node !in holders &&
            node.remaining >= BLOCK_SIZE &&
            holders.count { it.rack == node.rack } < maxReplicasPerRack

    fun best(): DataNode? = firstAccepted(cluster.nodesByRemaining())

    fun bestInRack(rack: String): DataNode? = firstAccepted(cluster.nodesByRemaining().filter { it.rack == rack })

    fun bestOutsideRack(rack: String): DataNode? = firstAccepted(cluster.nodesByRemaining().filter { it.rack != rack })

    private fun firstAccepted(nodesByRemaining: Sequence<DataNode>): DataNode? =
        nodesByRemaining.takeWhile { it.remaining >= BLOCK_SIZE }.firstOrNull(::accepts)
}

private fun maxReplicasPerRack(replication: Int, nodeCount: Int, rackCount: Int): Int {
    val replicas = minOf(replication, nodeCount)
    if (rackCount == 1 || replicas <= 1) return Int.MAX_VALUE
    val limit = (replicas - 1) / rackCount + 2
    return if (limit == replicas) limit - 1 else limit
}
