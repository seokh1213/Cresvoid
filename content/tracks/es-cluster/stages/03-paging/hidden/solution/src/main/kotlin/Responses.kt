fun statsLines(index: ShardedIndex): List<String> =
    index.shards.map { "shard ${it.number} docs ${it.documentCount} tokens ${it.tokenCount}" }

fun searchLines(response: SearchResponse): List<String> =
    listOf("hits ${response.totalHits} merged ${response.mergedHits}") +
        response.hits.map { hit ->
            "${hit.document.id} ${hit.shardNumber} " + response.sortKeys.joinToString(" ") { it.field.format(hit) }
        }

fun termsLines(response: TermsResponse): List<String> =
    listOf("terms ${response.approximateBuckets.size} error ${response.errorBound} other ${response.approximateOtherCount}") +
        response.approximateBuckets.map { "${it.brand} ${it.count} ${it.errorBound} ${it.exactCount}" } +
        "exact ${response.exactBuckets.size} other ${response.exactOtherCount}" +
        response.exactBuckets.map { "${it.brand} ${it.count}" }

fun openPitLine(pointInTime: PointInTime): String = "pit ${pointInTime.id}"
