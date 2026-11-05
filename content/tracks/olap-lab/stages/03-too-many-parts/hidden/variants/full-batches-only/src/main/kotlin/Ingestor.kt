class Ingestor(private val clickHouse: ClickHouse) {
    private val pending = mutableListOf<Event>()
    private val batchSize = 32

    @Synchronized
    fun accept(events: List<Event>) {
        pending.addAll(events)
        while (pending.size >= batchSize) {
            clickHouse.insert(pending.take(batchSize))
            pending.subList(0, batchSize).clear()
        }
    }

    @Synchronized
    fun flush() = Unit
}
