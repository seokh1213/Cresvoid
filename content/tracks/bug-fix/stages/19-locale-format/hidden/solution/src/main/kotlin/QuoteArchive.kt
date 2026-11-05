data class SavedQuote(
    val quotation: PriceQuotation,
    val documents: PreparedDocuments,
)

class QuoteArchive(
    private val catalog: PricingCatalog,
    private val documents: PriceDocuments,
) {
    private val quotes = linkedMapOf<String, SavedQuote>()

    fun freeze(quoteId: String, productId: String, quantity: Int): Boolean {
        val quotation = catalog.quote(productId, quantity) ?: return false
        val prepared = documents.prepare(quotation)
        quotes[quoteId] = SavedQuote(quotation, prepared)
        return true
    }

    fun find(quoteId: String): SavedQuote? = quotes[quoteId]
}
