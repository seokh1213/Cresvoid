class ShardedIndex(shardCount: Int) {
    val shards: List<Shard> = List(shardCount) { Shard(number = it) }

    fun put(command: Command.Put) {
        shardFor(command.routing).put(command.id, command.brand, command.price, tokenize(command.title))
    }

    fun delete(command: Command.Delete) {
        if (!shardFor(command.routing).delete(command.id)) reject(ErrorCode.NOT_FOUND)
    }

    private fun shardFor(routing: String): Shard = shards[(routingHash(routing) % shards.size).toInt()]
}
