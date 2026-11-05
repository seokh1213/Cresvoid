data class Product(val id: String, val seller: String, val attributes: Map<String, String>)
data class AttributeFilter(val key: String, val value: String)
data class Search(val seller: String?, val filters: List<AttributeFilter>)

class Catalog(private val elastic: Elastic) {
    fun initialize() {
        if (elastic.request("HEAD", "/${elastic.index}").statusCode() == 200) return
        val mapping = """
            {"settings":{"number_of_shards":1,"number_of_replicas":0,
              "refresh_interval":"-1","index.mapping.total_fields.limit":12,
              "index.mapping.total_fields.ignore_dynamic_beyond_limit":false},
             "mappings":{"dynamic":"strict","properties":{
              "id":{"type":"keyword"},"seller":{"type":"keyword"},
              "attributes":{"type":"object","dynamic":"strict","properties":{
                "key":{"type":"keyword"},"value":{"type":"keyword"}}}}}}
        """.trimIndent()
        elastic.requireSuccess("PUT", "/${elastic.index}", mapping)
    }

    fun put(product: Product) {
        val attributes = JsonArray(product.attributes.map { (key, value) ->
            jsonObject("key" to jsonString(key), "value" to jsonString(value))
        })
        val source = jsonObject(
            "id" to jsonString(product.id),
            "seller" to jsonString(product.seller),
            "attributes" to attributes,
        )
        elastic.requireSuccess("PUT", "/${elastic.index}/_doc/${product.id}", source.toIndentedJson())
    }

    fun search(search: Search): List<String> {
        val clauses = buildList {
            search.seller?.let { add(term("seller", it)) }
            for (filter in search.filters) {
                add(term("attributes.key", filter.key))
                add(term("attributes.value", filter.value))
            }
        }
        val body = jsonObject(
            "size" to JsonNumber("1000"),
            "query" to jsonObject("bool" to jsonObject("filter" to JsonArray(clauses))),
        )
        val response = elastic.requireSuccess("POST", "/${elastic.index}/_search", body.toIndentedJson())
        return parseObject(response).objectValue("hits").array("hits").map { hit ->
            (hit as JsonObject).string("_id")
        }.sorted()
    }

    fun refresh() {
        elastic.requireSuccess("POST", "/${elastic.index}/_refresh")
    }

    private fun term(field: String, value: String): JsonObject =
        jsonObject("term" to jsonObject(field to jsonString(value)))
}
