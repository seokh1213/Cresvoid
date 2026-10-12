private const val REPLICATION_WORK_MULTIPLIER = 2

class BlockManager(private val cluster: Cluster, private val clock: Clock) {
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
            block.replicas.forEach(cluster::delete)
        }
    }

    fun forgetReplicasOf(node: DataNode) {
        for (replica in node.replicas) {
            if (replica.known) updateKnownReplicas(replica.block) { node.forget(replica) }
        }
    }

    fun receiveBlockReport(node: DataNode) {
        for (replica in node.replicas) {
            if (!replica.known) updateKnownReplicas(replica.block) { node.learn(replica) }
        }
    }

    fun forgetAllAndReceiveReportsFromRunningNodes(allBlocks: Sequence<Block>) {
        // 노드마다 보고를 받으면 블록이 대기열을 여러 번 오가므로 결과를 블록 단위로 한 번에 반영한다
        for (block in allBlocks) {
            if (block.replicas.all { it.known == it.node.isRunning }) continue
            updateKnownReplicas(block) {
                for (replica in block.replicas) {
                    if (replica.node.isRunning) replica.node.learn(replica) else replica.node.forget(replica)
                }
            }
        }
    }

    fun overReplicatedBlocksOn(node: DataNode): List<Block> =
        node.replicas.map { it.block }.filter { redundancyOf(it) == Redundancy.OVER_REPLICATED }.sortedBy { it.id }

    fun deleteExcessReplicas(overReplicatedById: List<Block>): List<Event> = buildList {
        for (block in overReplicatedById) {
            while (block.knownReplicaCount > block.replication) {
                val excess = placement.chooseExcessReplica(block)
                updateKnownReplicas(block) { deleteReplica(excess) }
                add(Event.Delete(clock.now, block, excess.node))
            }
        }
    }

    fun runReplicationRound(): List<Event> {
        val limit = REPLICATION_WORK_MULTIPLIER * cluster.nonDeadNodeCount
        return health.underReplicatedBlocks(limit).flatMap(::replicate)
    }

    private fun replicate(block: Block): List<Event> {
        val source = block.knownLocations
            .filter { cluster.stateOf(it) == NodeState.LIVE }
            .minByOrNull { it.name }
            ?: return emptyList()
        return buildList {
            while (block.knownReplicaCount < block.replication) {
                val target = placement.chooseReplicationTarget(block, source) ?: break
                updateKnownReplicas(block) { storeReplica(block, target) }
                add(Event.Replicate(clock.now, block, source, target))
            }
        }
    }

    private fun placeReplicas(block: Block, writer: DataNode?) {
        while (block.knownReplicaCount < block.replication) {
            val target = placement.chooseTarget(block, writer) ?: return
            storeReplica(block, target)
        }
    }

    private fun storeReplica(block: Block, target: DataNode) {
        check(block.replicas.none { it.node == target })
        val replica = Replica(block, target)
        block.addReplica(replica)
        cluster.store(replica)
    }

    private fun deleteReplica(replica: Replica) {
        replica.block.removeReplica(replica)
        cluster.delete(replica)
    }

    private inline fun updateKnownReplicas(block: Block, change: () -> Unit) {
        health.untrack(block)
        change()
        health.track(block)
    }
}
