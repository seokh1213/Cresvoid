data class ProductCode(
    val original: String,
    val category: String,
    val serialNumber: Int,
)

class ProductCodeParser {
    private val pattern = Regex("([A-Z]{3})-([0-9]{4})")
    private val parsedByCode = mutableMapOf<String, ProductCode>()

    fun parse(original: String): ProductCode? {
        val lookupCode = original
        val parsed = parsedByCode[lookupCode] ?: parseUncached(original)?.also {
            parsedByCode[lookupCode] = it
        }
        return parsed?.copy(original = original)
    }

    private fun parseUncached(original: String): ProductCode? {
        val codeParts = pattern.matchEntire(original) ?: return null
        return productCode(original, codeParts)
    }

    fun extract(original: String): ProductCode? {
        val codeParts = pattern.find(original) ?: return null
        return productCode(original, codeParts)
    }

    private fun productCode(original: String, codeParts: MatchResult): ProductCode =
        ProductCode(
            original = original,
            category = codeParts.groupValues[1],
            serialNumber = codeParts.groupValues[2].toInt(),
        )
}

data class CatalogProduct(
    val id: String,
    val code: ProductCode,
) {
    fun registrationLine(): String =
        "OK $id ${code.category} ${code.serialNumber}"

    fun detailLine(): String =
        "PRODUCT $id ${code.original} ${code.category} ${code.serialNumber}"

    fun listingEntry(): String = "$id:${code.original}"
}

sealed interface RegistrationResult {
    data class Accepted(val product: CatalogProduct) : RegistrationResult
    data class Rejected(val productId: String, val reason: RejectionReason) : RegistrationResult
}

enum class RejectionReason {
    INVALID_CODE,
    DUPLICATE_CODE,
}
