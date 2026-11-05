class CatalogRejection(val reason: String) : RuntimeException(reason)

class SaleCatalog {
    private val products = mutableMapOf<String, Product>()
    private val saleIds = mutableSetOf<String>()
    private val receipts = mutableMapOf<String, Receipt>()

    fun registerProduct(sku: String, regularPrice: Long) {
        if (sku in products) throw CatalogRejection("DUPLICATE_PRODUCT")
        products[sku] = Product(sku, regularPrice)
    }

    fun scheduleSale(sku: String, offer: SaleOffer) {
        val product = product(sku)
        if (offer.id in saleIds) throw CatalogRejection("DUPLICATE_SALE")
        if (!offer.window.startsAt.isBefore(offer.window.endsAt)) {
            throw CatalogRejection("INVALID_WINDOW")
        }
        if (offer.unitPrice > product.regularPrice) {
            throw CatalogRejection("INVALID_PRICE")
        }
        product.schedule.add(offer)
        saleIds.add(offer.id)
    }

    fun placeOrder(request: OrderRequest): Receipt {
        if (request.id in receipts) throw CatalogRejection("DUPLICATE_ORDER")
        val product = product(request.sku)
        val price = product.priceAt(request.placedAt)
        val receipt = Receipt(
            orderId = request.id,
            sku = product.sku,
            quantity = request.quantity,
            price = price,
        )
        receipts[request.id] = receipt
        return receipt
    }

    fun receipt(orderId: String): Receipt =
        receipts[orderId] ?: throw CatalogRejection("MISSING_ORDER")

    private fun product(sku: String): Product =
        products[sku] ?: throw CatalogRejection("MISSING_PRODUCT")
}

class Product(val sku: String, val regularPrice: Long) {
    val schedule = SaleSchedule()

    fun priceAt(placedAt: java.time.Instant): OrderPrice {
        val offer = schedule.offerAt(placedAt)
        return if (offer == null) {
            OrderPrice(regularPrice, null)
        } else {
            OrderPrice(offer.unitPrice, offer.id)
        }
    }
}
