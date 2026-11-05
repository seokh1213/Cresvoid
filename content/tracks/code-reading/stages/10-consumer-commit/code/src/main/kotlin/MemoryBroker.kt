data class OrderRecord(val partition: Int, val offset: Int, val order: OrderEvent)

class MemoryBroker {
    private val logs = sortedMapOf(0 to mutableListOf<OrderRecord>(), 1 to mutableListOf())
    private val committed = sortedMapOf(0 to 0, 1 to 0)

    fun append(partition: Int, order: OrderEvent) {
        val partitionLog = logs.getValue(partition)
        partitionLog.add(OrderRecord(partition, partitionLog.size, order))
    }

    fun committedOffset(partition: Int): Int = committed.getValue(partition)

    fun fetch(positions: Map<Int, Int>, limit: Int): List<OrderRecord> =
        positions.toSortedMap().flatMap { (partition, offset) ->
            logs.getValue(partition).drop(offset)
        }.take(limit)

    fun commit(nextOffsets: Map<Int, Int>) {
        nextOffsets.forEach { (partition, offset) ->
            require(offset in committed.getValue(partition)..logs.getValue(partition).size)
        }
        committed.putAll(nextOffsets)
    }

    fun describeOffsets(): String = committed.entries.joinToString(",") { (partition, offset) ->
        "$partition:$offset"
    }
}

class PartitionLease(private val broker: MemoryBroker) {
    private val positions = sortedMapOf<Int, Int>()

    fun assign(partitions: Set<Int>) {
        positions.clear()
        partitions.sorted().forEach { positions[it] = broker.committedOffset(it) }
    }

    fun poll(limit: Int): List<OrderRecord> {
        require(limit > 0)
        val records = broker.fetch(positions, limit)
        records.forEach { positions[it.partition] = it.offset + 1 }
        return records
    }

    fun partitions(): Set<Int> = positions.keys.toSet()

    fun positionSnapshot(): Map<Int, Int> = positions.toMap()
}
