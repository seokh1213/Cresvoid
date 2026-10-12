sealed interface Exchange {
    val partitions: Int
}

data class HashExchange(override val partitions: Int, val combiner: Reducer?) : Exchange

data class RoundRobinExchange(override val partitions: Int) : Exchange

class ShuffleWriter(private val dictionary: KeyDictionary, private val grouping: KeyGrouping) {
    fun write(mapTasks: List<Partition>, exchange: Exchange): List<Partition> {
        val combiner = (exchange as? HashExchange)?.combiner
        val sent = if (combiner == null) mapTasks else mapTasks.map { grouping.combine(it, combiner) }
        val destinations = sent.mapIndexed { task, records -> destinationsOf(records, task, exchange) }
        return distribute(sent, destinations, exchange.partitions)
    }

    private fun destinationsOf(records: Partition, task: Int, exchange: Exchange): IntArray = when (exchange) {
        is HashExchange -> IntArray(records.size) { dictionary.partitionOf(records.keys[it], exchange.partitions) }
        is RoundRobinExchange -> IntArray(records.size) { (task + it) % exchange.partitions }
    }

    private fun distribute(sent: List<Partition>, destinations: List<IntArray>, partitionCount: Int): List<Partition> {
        val partitionStarts = IntArray(partitionCount + 1)
        for (destination in destinations) {
            for (partition in destination) partitionStarts[partition + 1]++
        }
        for (partition in 0 until partitionCount) partitionStarts[partition + 1] += partitionStarts[partition]
        val cursors = partitionStarts.copyOf()
        val arrivalOrder = IntArray(partitionStarts[partitionCount])
        var sentRow = 0
        for ((task, records) in sent.withIndex()) {
            for (row in 0 until records.size) arrivalOrder[cursors[destinations[task][row]]++] = sentRow++
        }
        val allSent = Partition.concat(sent)
        return List(partitionCount) { allSent.gather(arrivalOrder, partitionStarts[it], partitionStarts[it + 1]) }
    }
}
