import java.io.BufferedWriter

fun export(elastic: Elastic, index: String, output: BufferedWriter) {
    var pit = elastic.request("POST", "/$index/_pit?keep_alive=2m").obj().getValue("id").text()
    try {
        var after: JsonValue? = null
        while (true) {
            val cursor = after?.let { ",\"search_after\":${it.toIndentedJson()}" }.orEmpty()
            val response = elastic.request("POST", "/_search", """
                {"size":1000,"track_total_hits":false,"pit":{"id":${quoted(pit)},"keep_alive":"2m"},
                 "sort":[{"rank":"asc"},{"id":"asc"}]$cursor}
            """.trimIndent())
            pit = response.obj()["pit_id"]?.text() ?: pit
            val hits = pageHits(response)
            if (hits.isEmpty()) break
            hits.forEach { writeProduct(it, output) }
            output.flush()
            // PIT adds a tiebreaker; carry the entire returned tuple into the next request.
            after = hits.last().obj().getValue("sort")
        }
    } finally {
        elastic.request("DELETE", "/_pit", """{"id":${quoted(pit)}}""")
    }
}
