sealed interface Exchange {
    val partitions: Int
}

data class HashExchange(override val partitions: Int, val combiner: Reducer?) : Exchange

data class RoundRobinExchange(override val partitions: Int) : Exchange

class ShuffleOutput(
    val received: List<Partition>,
    val blockStarts: List<IntArray>,
    val blockBytes: List<LongArray>,
    val spills: Long,
) {
    val sizes: LongArray = LongArray(received.size) { blockBytes[it].sum() }
    val records: Int get() = received.sumOf { it.size }
    val bytes: Long get() = sizes.sum()
}

class ShuffleWriter(private val dictionary: KeyDictionary, private val grouping: KeyGrouping) {
    private val bufferedKeys = KeyMarks(dictionary)

    fun write(mapTasks: List<Partition>, exchange: Exchange, memoryLimit: Long?): ShuffleOutput {
        val combiner = (exchange as? HashExchange)?.combiner
        val sent = if (combiner == null) mapTasks else mapTasks.map { grouping.combine(it, combiner) }
        val spills = if (memoryLimit == null) 0L else mapTasks.sumOf { spillsOf(it, combiner != null, memoryLimit) }
        val destinations = sent.mapIndexed { task, records -> destinationsOf(records, task, exchange) }
        return distribute(sent, destinations, exchange.partitions, spills)
    }

    private fun destinationsOf(records: Partition, task: Int, exchange: Exchange): IntArray = when (exchange) {
        is HashExchange -> IntArray(records.size) { dictionary.partitionOf(records.keys[it], exchange.partitions) }
        is RoundRobinExchange -> IntArray(records.size) { (task + it) % exchange.partitions }
    }

    private fun spillsOf(task: Partition, combinesByKey: Boolean, memoryLimit: Long): Long {
        var buffered = 0L
        var spills = 0L
        bufferedKeys.clear()
        for (row in 0 until task.size) {
            if (!combinesByKey || bufferedKeys.markFirst(task.keys[row])) buffered += task.rowBytes(row, dictionary)
            if (buffered > memoryLimit) {
                spills++
                buffered = 0
                bufferedKeys.clear()
            }
        }
        return spills
    }

    private fun distribute(sent: List<Partition>, destinations: List<IntArray>, partitionCount: Int, spills: Long): ShuffleOutput {
        val taskCount = sent.size
        val blockStarts = List(partitionCount) { IntArray(taskCount + 1) }
        val blockBytes = List(partitionCount) { LongArray(taskCount) }
        for ((task, records) in sent.withIndex()) {
            for (row in 0 until records.size) {
                val destination = destinations[task][row]
                blockStarts[destination][task + 1]++
                blockBytes[destination][task] += records.rowBytes(row, dictionary).toLong()
            }
        }
        for (starts in blockStarts) {
            for (task in 1..taskCount) starts[task] += starts[task - 1]
        }

        val partitionStarts = IntArray(partitionCount + 1)
        for (partition in 0 until partitionCount) {
            partitionStarts[partition + 1] = partitionStarts[partition] + blockStarts[partition][taskCount]
        }
        val cursors = partitionStarts.copyOf()
        val arrivalOrder = IntArray(partitionStarts[partitionCount])
        var sentRow = 0
        for ((task, records) in sent.withIndex()) {
            for (row in 0 until records.size) arrivalOrder[cursors[destinations[task][row]]++] = sentRow++
        }
        val allSent = Partition.concat(sent)
        val received = List(partitionCount) { allSent.gather(arrivalOrder, partitionStarts[it], partitionStarts[it + 1]) }
        return ShuffleOutput(received, blockStarts, blockBytes, spills)
    }
}
