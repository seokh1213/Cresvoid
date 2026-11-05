class CatalogUploads(private val codeParser: ProductCodeParser) {
    private val pending = mutableMapOf<String, LinkedHashMap<String, String>>()

    fun stage(batchId: String, productId: String, code: String) {
        pending.getOrPut(batchId) { linkedMapOf() }[productId] = code
    }

    fun check(batchId: String, catalog: ProductCatalog): UploadSummary? {
        val rows = pending[batchId] ?: return null
        return process(rows, catalog.snapshot())
    }

    fun apply(batchId: String, catalog: ProductCatalog): UploadSummary? {
        val rows = pending.remove(batchId) ?: return null
        return process(rows, catalog)
    }

    private fun process(rows: Map<String, String>, catalog: ProductCatalog): UploadSummary {
        var accepted = 0
        var rejected = 0
        for ((productId, code) in rows) {
            val parsedCode = codeParser.parse(code)
            when (catalog.register(productId, parsedCode)) {
                is RegistrationResult.Accepted -> accepted++
                is RegistrationResult.Rejected -> rejected++
            }
        }
        return UploadSummary(accepted, rejected)
    }
}

data class UploadSummary(val accepted: Int, val rejected: Int) {
    fun line(operation: String, batchId: String): String =
        "$operation $batchId $accepted $rejected"
}
