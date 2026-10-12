import java.util.TreeMap

class RwDependencies {
    private val activeNodes = HashMap<Transaction, Node>()
    private val activeInStartOrder = ArrayDeque<Node>()
    private val committedInCommitOrder = ArrayDeque<Node>()
    private val readersByKey = HashMap<String, MutableSet<Node>>()
    private val rangeReads = LinkedHashSet<RangeRead>()
    private val writersByKey = TreeMap<String, MutableSet<Node>>()

    fun track(transaction: Transaction) {
        val node = Node(transaction)
        activeNodes[transaction] = node
        activeInStartOrder.addLast(node)
    }

    fun recordRead(transaction: Transaction, key: String) {
        val reader = activeNodes[transaction] ?: return
        reader.readKeys.add(key)
        readersByKey.getOrPut(key) { LinkedHashSet() }.add(reader)
        writersByKey[key]?.forEach { addDependency(reader, it) }
    }

    fun recordRangeRead(transaction: Transaction, range: KeyRange) {
        val reader = activeNodes[transaction] ?: return
        val read = RangeRead(reader, range)
        reader.rangeReads.add(read)
        rangeReads.add(read)
        for (writers in writersByKey.subMap(range.start, range.end).values) {
            writers.forEach { addDependency(reader, it) }
        }
    }

    fun recordWrite(transaction: Transaction, key: String) {
        val writer = activeNodes[transaction] ?: return
        writer.writtenKeys.add(key)
        writersByKey.getOrPut(key) { LinkedHashSet() }.add(writer)
        readersByKey[key]?.forEach { addDependency(it, writer) }
        rangeReads.filter { key in it.range }.forEach { addDependency(it.reader, writer) }
    }

    fun inDangerousStructureWithCommitted(transaction: Transaction): Boolean {
        val node = activeNodes[transaction] ?: return false
        return dangerousStructuresWith(node).any { structure -> structure.any { it !== node && it.isCommitted } }
    }

    fun committed(transaction: Transaction) {
        val node = activeNodes.remove(transaction) ?: return
        committedInCommitOrder.addLast(node)
        forgetUnusableRecords()
    }

    fun aborted(transaction: Transaction) {
        val node = activeNodes.remove(transaction) ?: return
        node.outgoing.forEach { it.incoming.remove(node) }
        node.incoming.forEach { it.outgoing.remove(node) }
        forgetRecords(node)
        forgetUnusableRecords()
    }

    private fun addDependency(reader: Node, writer: Node) {
        if (reader === writer || !reader.transaction.ranConcurrentlyWith(writer.transaction)) return
        reader.outgoing.add(writer)
        writer.incoming.add(reader)
    }

    private fun dangerousStructuresWith(node: Node): Sequence<List<Node>> =
        sequence {
            for (reader in node.incoming) for (writer in node.outgoing) yield(listOf(reader, node, writer))
            for (pivot in node.outgoing) for (writer in pivot.outgoing) yield(listOf(node, pivot, writer))
            for (pivot in node.incoming) for (reader in pivot.incoming) yield(listOf(reader, pivot, node))
        }

    // 커밋한 트랜잭션은 그보다 먼저 시작해 아직 활동 중인 트랜잭션하고만 새 rw 의존을 만들 수 있다
    private fun forgetUnusableRecords() {
        while (activeInStartOrder.isNotEmpty() && activeInStartOrder.first().transaction.status != TransactionStatus.ACTIVE) {
            activeInStartOrder.removeFirst()
        }
        val oldestActive = activeInStartOrder.firstOrNull()?.transaction
        while (committedInCommitOrder.isNotEmpty()) {
            val oldestCommitted = committedInCommitOrder.first()
            if (oldestActive != null && oldestCommitted.transaction.ranConcurrentlyWith(oldestActive)) return
            forgetRecords(committedInCommitOrder.removeFirst())
        }
    }

    private fun forgetRecords(node: Node) {
        node.readKeys.forEach { readersByKey.removeNode(it, node) }
        node.writtenKeys.forEach { writersByKey.removeNode(it, node) }
        node.rangeReads.forEach(rangeReads::remove)
    }

    private fun MutableMap<String, MutableSet<Node>>.removeNode(key: String, node: Node) {
        val nodes = get(key) ?: return
        nodes.remove(node)
        if (nodes.isEmpty()) remove(key)
    }
}

private class Node(val transaction: Transaction) {
    val outgoing = LinkedHashSet<Node>()
    val incoming = LinkedHashSet<Node>()
    val readKeys = HashSet<String>()
    val rangeReads = ArrayList<RangeRead>()
    val writtenKeys = HashSet<String>()

    val isCommitted: Boolean
        get() = transaction.status == TransactionStatus.COMMITTED
}

private class RangeRead(val reader: Node, val range: KeyRange)
