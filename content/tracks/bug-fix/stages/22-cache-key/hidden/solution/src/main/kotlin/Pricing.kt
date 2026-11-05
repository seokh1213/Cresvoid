enum class MemberTier(val discountPercent: Int) {
    REGULAR(0),
    SILVER(5),
    VIP(20),
}

enum class Coupon {
    ON,
    OFF,
}

data class Product(
    val id: String,
    val basePrice: Long,
    val ttlSeconds: Long,
)

data class PriceRequest(
    val productId: String,
    val tier: MemberTier,
    val coupon: Coupon,
) {
    fun cacheKey(): String = "$productId:${tier.name}:${coupon.name}"
}

data class ProductPrice(val productId: String, val amount: Long?) {
    fun singleLine(): String = when (amount) {
        null -> "MISSING $productId"
        else -> "PRICE $productId $amount"
    }

    fun listItem(): String = "$productId:${amount ?: "-"}"
}

class PriceCalculator {
    fun calculate(product: Product, request: PriceRequest): Long {
        val discounted = product.basePrice * (100 - request.tier.discountPercent) / 100
        return when (request.coupon) {
            Coupon.OFF -> discounted
            Coupon.ON -> (discounted - 300).coerceAtLeast(0)
        }
    }
}
