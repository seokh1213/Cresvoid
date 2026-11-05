object Resources {
    fun list(params: JsonObject): JsonObject {
        val entries = Catalog.products.map { product ->
            obj("uri" to str(uri(product)), "name" to str(product.id), "mimeType" to str("text/plain"))
        }
        return paginatedList("resources", params, entries)
    }

    fun read(params: JsonObject): JsonObject {
        val uri = params["uri"].requireString()
        val product = Catalog.products.firstOrNull { uri(it) == uri } ?: invalidParams()
        return cacheableResult("contents" to arr(contents(product)))
    }

    fun contents(product: Product): JsonObject = obj(
        "uri" to str(uri(product)), "mimeType" to str("text/plain"), "text" to str(product.text()),
    )

    private fun uri(product: Product): String = "shop://products/${product.id}"
}
