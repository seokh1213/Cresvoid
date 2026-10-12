fun statsLines(index: ShardedIndex): List<String> =
    index.shards.map { "shard ${it.number} docs ${it.documentCount} tokens ${it.tokenCount}" }

fun searchLines(response: SearchResponse): List<String> =
    listOf("hits ${response.totalHits} merged ${response.mergedHits}") +
        response.hits.map { "${it.document.id} ${it.shardNumber} ${it.score}" }
