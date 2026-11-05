data class ProductPrice(
    val id: String,
    val unitPrice: Long,
    val discountBasisPoints: Int,
) {
    init {
        require(unitPrice in 0..1_000_000_000L)
        require(discountBasisPoints in 0..10_000)
    }

    fun quote(quantity: Int): PriceQuotation {
        require(quantity in 1..10_000)
        val originalAmount = unitPrice * quantity
        val discountAmount = originalAmount * discountBasisPoints / 10_000
        return PriceQuotation(
            productId = id,
            quantity = quantity,
            originalAmount = originalAmount,
            discountAmount = discountAmount,
            discountBasisPoints = discountBasisPoints,
        )
    }
}

data class PriceQuotation(
    val productId: String,
    val quantity: Int,
    val originalAmount: Long,
    val discountAmount: Long,
    val discountBasisPoints: Int,
) {
    val payableAmount: Long
        get() = originalAmount - discountAmount
}

class PricingCatalog {
    private val products = linkedMapOf<String, ProductPrice>()

    val size: Int
        get() = products.size

    fun put(product: ProductPrice) {
        products[product.id] = product
    }

    fun find(productId: String): ProductPrice? = products[productId]

    fun quote(productId: String, quantity: Int): PriceQuotation? {
        val product = products[productId] ?: return null
        return product.quote(quantity)
    }
}
