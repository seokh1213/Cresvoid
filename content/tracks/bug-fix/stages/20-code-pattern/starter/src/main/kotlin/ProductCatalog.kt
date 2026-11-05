class ProductCatalog {
    private val productsById = mutableMapOf<String, CatalogProduct>()

    fun register(productId: String, code: ProductCode?): RegistrationResult {
        if (code == null) return RegistrationResult.Rejected(productId, RejectionReason.INVALID_CODE)
        val owner = findCodeOwner(code.original)
        if (owner != null && owner.id != productId) {
            return RegistrationResult.Rejected(productId, RejectionReason.DUPLICATE_CODE)
        }
        val product = CatalogProduct(productId, code)
        productsById[productId] = product
        return RegistrationResult.Accepted(product)
    }

    fun find(productId: String): CatalogProduct? = productsById[productId]

    fun listCategory(category: String): List<CatalogProduct> =
        productsById.values
            .filter { it.code.category == category }
            .sortedBy { it.id }

    fun remove(productId: String): CatalogProduct? = productsById.remove(productId)

    fun snapshot(): ProductCatalog = ProductCatalog().also { snapshot ->
        snapshot.productsById.putAll(productsById)
    }

    private fun findCodeOwner(originalCode: String): CatalogProduct? =
        productsById.values.firstOrNull { it.code.original == originalCode }
}

class CatalogCommands(
    private val catalog: ProductCatalog,
    private val codeParser: ProductCodeParser,
) {
    private val uploads = CatalogUploads(codeParser)

    fun execute(command: CatalogCommand): String = when (command) {
        is CatalogCommand.Register -> register(command)
        is CatalogCommand.Get -> get(command.productId)
        is CatalogCommand.ListCategory -> listCategory(command.category)
        is CatalogCommand.Remove -> remove(command.productId)
        is CatalogCommand.Stage -> stage(command)
        is CatalogCommand.Check -> review(command.batchId)
        is CatalogCommand.Apply -> apply(command.batchId)
        is CatalogCommand.Scan -> scan(command.text)
        CatalogCommand.Invalid -> "ERROR COMMAND"
    }

    private fun register(command: CatalogCommand.Register): String =
        when (val result = catalog.register(command.productId, codeParser.parse(command.code))) {
            is RegistrationResult.Accepted -> result.product.registrationLine()
            is RegistrationResult.Rejected -> "REJECTED ${result.productId} ${result.reason}"
        }

    private fun scan(text: String): String {
        val code = codeParser.extract(text) ?: return "NOT_FOUND"
        return "FOUND ${code.category} ${code.serialNumber}"
    }

    private fun stage(command: CatalogCommand.Stage): String {
        uploads.stage(command.batchId, command.productId, command.code)
        return "STAGED ${command.batchId} ${command.productId}"
    }

    private fun review(batchId: String): String {
        val summary = uploads.check(batchId, catalog) ?: return "MISSING_BATCH $batchId"
        return summary.line("REVIEW", batchId)
    }

    private fun apply(batchId: String): String {
        val summary = uploads.apply(batchId, catalog) ?: return "MISSING_BATCH $batchId"
        return summary.line("APPLIED", batchId)
    }

    private fun get(productId: String): String {
        val product = catalog.find(productId) ?: return "MISSING $productId"
        return product.detailLine()
    }

    private fun listCategory(category: String): String {
        val products = catalog.listCategory(category)
        if (products.isEmpty()) return "(none)"
        return products.joinToString(",") { it.listingEntry() }
    }

    private fun remove(productId: String): String {
        val removed = catalog.remove(productId) ?: return "MISSING $productId"
        return "REMOVED ${removed.id}"
    }
}
