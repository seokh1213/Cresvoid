import java.util.TreeMap
import java.util.TreeSet

private val MOST_REMAINING_FIRST = Comparator<DataNode> { first, second ->
    when {
        first.remaining != second.remaining -> second.remaining.compareTo(first.remaining)
        else -> first.name.compareTo(second.name)
    }
}

class Cluster {
    private val nodesByName = TreeMap<String, DataNode>()
    private val racks = HashSet<String>()
    private val byRemaining = TreeSet(MOST_REMAINING_FIRST)

    val nodeCount: Int get() = nodesByName.size

    val rackCount: Int get() = racks.size

    fun register(name: String, rack: String, capacity: Long) {
        if (name in nodesByName) reject(ErrorCode.NODE_EXISTS)
        val node = DataNode(name, rack, capacity)
        nodesByName[name] = node
        racks += rack
        byRemaining += node
    }

    fun find(name: String): DataNode = nodesByName[name] ?: reject(ErrorCode.NO_NODE)

    fun nodesByRemaining(): Sequence<DataNode> = byRemaining.asSequence()

    fun addReplica(node: DataNode, block: Block) = rerank(node) { node.addReplica(block) }

    fun removeReplica(node: DataNode, block: Block) = rerank(node) { node.removeReplica(block) }

    private inline fun rerank(node: DataNode, changeUsage: () -> Unit) {
        byRemaining -= node
        changeUsage()
        byRemaining += node
    }
}
