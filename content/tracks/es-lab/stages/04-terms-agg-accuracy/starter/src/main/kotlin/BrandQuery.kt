fun brandQuery(size: Int, shardSize: Int?): String {
    val candidates = shardSize ?: size
    return """{"size":0,"aggs":{"brands":{"terms":{"field":"brand","size":$size,"shard_size":$candidates,"order":{"_count":"desc"},"show_term_doc_count_error":true}}}}"""
}

fun reportBody(body: String): String = body
