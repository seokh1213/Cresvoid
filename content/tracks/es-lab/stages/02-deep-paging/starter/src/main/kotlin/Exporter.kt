import java.io.BufferedWriter

fun export(elastic: Elastic, index: String, output: BufferedWriter) {
    var offset = 0
    while (true) {
        val response = elastic.request("POST", "/$index/_search", """
            {"size":1000,"from":$offset,"track_total_hits":false,"sort":[{"rank":"asc"},{"id":"asc"}]}
        """.trimIndent())
        val hits = pageHits(response)
        if (hits.isEmpty()) break
        hits.forEach { writeProduct(it, output) }
        output.flush()
        offset += hits.size
    }
}
