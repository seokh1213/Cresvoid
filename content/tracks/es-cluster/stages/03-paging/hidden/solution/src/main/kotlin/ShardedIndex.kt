class ShardedIndex(shardCount: Int) {
    val shards: List<Shard> = List(shardCount) { Shard(number = it) }

    val exactBrandCounts = BrandCounts()

    private val pointsInTime = HashMap<Int, PointInTime>()
    private var nextPointInTimeId = 1

    val documentCount: Int get() = shards.sumOf { it.documentCount }

    fun put(command: Command.Put) {
        val replaced = shardFor(command.routing).put(command.id, command.brand, command.price, tokenize(command.title))
        replaced?.let { exactBrandCounts.decrement(it.brand) }
        exactBrandCounts.increment(command.brand)
    }

    fun delete(command: Command.Delete) {
        val removed = shardFor(command.routing).delete(command.id) ?: reject(ErrorCode.NOT_FOUND)
        exactBrandCounts.decrement(removed.brand)
    }

    fun openPointInTime(): PointInTime {
        val pointInTime = PointInTime(nextPointInTimeId++, shards.map(Shard::freeze))
        pointsInTime[pointInTime.id] = pointInTime
        return pointInTime
    }

    fun closePointInTime(id: Int) {
        pointsInTime.remove(id) ?: reject(ErrorCode.NOT_FOUND)
    }

    fun findPointInTime(id: Int): PointInTime = pointsInTime[id] ?: reject(ErrorCode.NOT_FOUND)

    private fun shardFor(routing: String): Shard = shards[(routingHash(routing) % shards.size).toInt()]
}
