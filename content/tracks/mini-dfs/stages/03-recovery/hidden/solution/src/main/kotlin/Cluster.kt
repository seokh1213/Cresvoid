import java.util.TreeMap
import java.util.TreeSet

private val MOST_REMAINING_FIRST = Comparator<DataNode> { first, second ->
    when {
        first.remaining != second.remaining -> second.remaining.compareTo(first.remaining)
        else -> first.name.compareTo(second.name)
    }
}

class Cluster(private val clock: Clock) {
    private val nodesByName = TreeMap<String, DataNode>()
    private val stoppedNodesByName = TreeMap<String, DataNode>()
    private val byRemaining = TreeSet(MOST_REMAINING_FIRST)
    private val nonDeadNodesPerRack = HashMap<String, Int>()

    var nonDeadNodeCount: Int = 0
        private set

    val nonDeadRackCount: Int get() = nonDeadNodesPerRack.size

    val stoppedNodes: Collection<DataNode> get() = stoppedNodesByName.values

    fun register(name: String, rack: String, capacity: Long) {
        if (name in nodesByName) reject(ErrorCode.NODE_EXISTS)
        val node = DataNode(name, rack, capacity)
        nodesByName[name] = node
        byRemaining += node
        countAsNonDead(node)
    }

    fun find(name: String): DataNode = nodesByName[name] ?: reject(ErrorCode.NO_NODE)

    fun stateOf(node: DataNode): NodeState = node.stateAt(clock.now)

    fun stop(node: DataNode) {
        if (!node.isRunning) reject(ErrorCode.NODE_STOPPED)
        node.stop(clock.now)
        stoppedNodesByName[node.name] = node
    }

    fun start(node: DataNode) {
        if (node.isRunning) reject(ErrorCode.NODE_RUNNING)
        if (node.liveness == Liveness.Dead) countAsNonDead(node)
        node.start()
        stoppedNodesByName.remove(node.name)
    }

    fun markDead(node: DataNode) {
        node.markDead()
        uncountAsNonDead(node)
    }

    fun nodesByRemaining(): Sequence<DataNode> = byRemaining.asSequence()

    fun store(replica: Replica) = rerank(replica.node) { replica.node.store(replica) }

    fun delete(replica: Replica) = rerank(replica.node) { replica.node.delete(replica) }

    private fun countAsNonDead(node: DataNode) {
        nonDeadNodesPerRack.merge(node.rack, 1, Int::plus)
        nonDeadNodeCount++
    }

    private fun uncountAsNonDead(node: DataNode) {
        nonDeadNodesPerRack.computeIfPresent(node.rack) { _, count -> (count - 1).takeIf { it > 0 } }
        nonDeadNodeCount--
    }

    private inline fun rerank(node: DataNode, changeUsage: () -> Unit) {
        byRemaining -= node
        changeUsage()
        byRemaining += node
    }
}
