fun brandQuery(size: Int, shardSize: Int?): String {
    val candidates = shardSize?.let { ",\"shard_size\":$it" }.orEmpty()
    return """{"size":0,"aggs":{"brands":{"terms":{"field":"brand","size":$size$candidates,"order":{"_count":"desc"},"show_term_doc_count_error":true}}}}"""
}

fun reportBody(body: String): String = body
