data class ImportedProduct(
    val line: Int,
    val sku: String,
    val name: String,
    val price: Int,
    val stock: Int,
)

data class RejectedProduct(
    val line: Int,
    val reason: String,
)

sealed interface ImportResult {
    data class Completed(
        val accepted: List<ImportedProduct>,
        val rejected: List<RejectedProduct>,
    ) : ImportResult

    data class Invalid(val reason: String) : ImportResult
}

fun importProducts(source: String): ImportResult {
    // BOM is a document prefix, not part of a quoted field.
    val csv = source.removePrefix("\uFEFF")
    val records = try {
        CsvReader(csv).readAll()
    } catch (failure: CsvSyntax) {
        return ImportResult.Invalid("CSV@${failure.line}:${failure.reason}")
    }
    val first = records.firstOrNull() ?: return ImportResult.Invalid("EMPTY")
    val header = try {
        ImportHeader.from(first)
    } catch (failure: HeaderProblem) {
        return ImportResult.Invalid("HEADER:${failure.reason}")
    }
    return validateProducts(header, records.drop(1))
}

private fun validateProducts(header: ImportHeader, records: List<CsvRecord>): ImportResult {
    val accepted = mutableListOf<ImportedProduct>()
    val rejected = mutableListOf<RejectedProduct>()
    val registeredSkus = mutableSetOf<String>()
    for (record in records) {
        val validation = validateProduct(header, record)
        when (validation) {
            is RowValidation.Invalid -> rejected.add(RejectedProduct(record.line, validation.reason))
            is RowValidation.Valid -> {
                val product = validation.product
                if (registeredSkus.add(product.sku)) accepted.add(product)
                else rejected.add(RejectedProduct(record.line, "DUPLICATE_SKU"))
            }
        }
    }
    return ImportResult.Completed(accepted.toList(), rejected.toList())
}

private sealed interface RowValidation {
    data class Valid(val product: ImportedProduct) : RowValidation
    data class Invalid(val reason: String) : RowValidation
}

private fun validateProduct(header: ImportHeader, record: CsvRecord): RowValidation {
    if (record.fields.size != header.width) return RowValidation.Invalid("COLUMNS")
    val sku = header.value(record, "sku").orEmpty().trim()
    if (!sku.matches(Regex("[A-Za-z0-9_-]{1,20}"))) {
        return RowValidation.Invalid("SKU")
    }
    val name = header.value(record, "name").orEmpty().trim()
    if (name.isEmpty()) return RowValidation.Invalid("NAME")
    val price = header.value(record, "price").orEmpty().trim().toIntOrNull()
    if (price == null || price <= 0) {
        return RowValidation.Invalid("PRICE")
    }
    val rawStock = header.value(record, "stock")
    val stock = if (rawStock == null) 0 else rawStock.trim().toIntOrNull()
    if (stock == null || stock < 0) {
        return RowValidation.Invalid("STOCK")
    }
    return RowValidation.Valid(ImportedProduct(record.line, sku, name, price, stock))
}
