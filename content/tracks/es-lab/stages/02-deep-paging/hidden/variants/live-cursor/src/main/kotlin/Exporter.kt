import java.io.BufferedWriter

fun export(elastic: Elastic, index: String, output: BufferedWriter) {
    var after: JsonValue? = null
    while (true) {
        val cursor = after?.let { ",\"search_after\":${it.toIndentedJson()}" }.orEmpty()
        val response = elastic.request("POST", "/$index/_search", """
            {"size":1000,"track_total_hits":false,"sort":[{"rank":"asc"},{"id":"asc"}]$cursor}
        """.trimIndent())
        val hits = pageHits(response)
        if (hits.isEmpty()) break
        hits.forEach { writeProduct(it, output) }
        output.flush()
        after = hits.last().obj().getValue("sort")
    }
}
