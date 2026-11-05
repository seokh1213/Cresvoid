class Ingestor(private val clickHouse: ClickHouse) {
    @Synchronized
    fun accept(events: List<Event>) {
        clickHouse.insert(events)
    }

    @Synchronized
    fun flush() = Unit
}
