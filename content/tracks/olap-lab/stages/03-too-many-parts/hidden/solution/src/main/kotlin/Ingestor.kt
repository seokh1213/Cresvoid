class Ingestor(private val clickHouse: ClickHouse) {
    private val pending = mutableListOf<Event>()
    private val batchSize = 32

    @Synchronized
    fun accept(events: List<Event>) {
        pending.addAll(events)
        while (pending.size >= batchSize) {
            insertPrefix(batchSize)
        }
    }

    @Synchronized
    fun flush() {
        if (pending.isNotEmpty()) insertPrefix(pending.size)
    }

    private fun insertPrefix(size: Int) {
        clickHouse.insert(pending.take(size))
        pending.subList(0, size).clear()
    }
}
