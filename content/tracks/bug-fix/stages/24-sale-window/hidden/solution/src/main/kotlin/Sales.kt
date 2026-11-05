import java.time.Instant

data class SaleWindow(val startsAt: Instant, val endsAt: Instant) {
    fun includes(placedAt: Instant): Boolean =
        !placedAt.isBefore(startsAt) && placedAt.isBefore(endsAt)

    fun overlaps(other: SaleWindow): Boolean =
        startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt)
}

data class SaleOffer(
    val id: String,
    val window: SaleWindow,
    val unitPrice: Long,
)

class SaleSchedule {
    private val offers = mutableListOf<SaleOffer>()

    fun add(offer: SaleOffer) {
        val found = offers.binarySearchBy(offer.window.startsAt) { it.window.startsAt }
        val insertionIndex = if (found >= 0) found else -found - 1
        val neighbors = listOfNotNull(
            offers.getOrNull(insertionIndex - 1),
            offers.getOrNull(insertionIndex),
        )
        if (neighbors.any { it.window.overlaps(offer.window) }) {
            throw CatalogRejection("SALE_CONFLICT")
        }
        offers.add(insertionIndex, offer)
    }

    fun offerAt(placedAt: Instant): SaleOffer? =
        offers.firstOrNull { it.window.includes(placedAt) }
}

data class OrderRequest(
    val id: String,
    val sku: String,
    val quantity: Int,
    val placedAt: Instant,
)

data class OrderPrice(val unitPrice: Long, val saleId: String?)

data class Receipt(
    val orderId: String,
    val sku: String,
    val quantity: Int,
    val price: OrderPrice,
) {
    val total: Long
        get() = price.unitPrice * quantity

    fun render(): String = listOf(
        "ORDER",
        orderId,
        sku,
        quantity,
        price.unitPrice,
        total,
        price.saleId ?: "-",
    ).joinToString(" ")
}
