import java.math.BigDecimal

data class CartQuote(
    val subtotal: BigDecimal,
    val shippingFee: BigDecimal,
) {
    val total: BigDecimal
        get() = subtotal + shippingFee
}

class ShippingPolicy {
    private val freeShippingMinimum = BigDecimal("50.00")
    private val standardShippingFee = BigDecimal("4.99")

    fun quote(lines: List<CartLine>): CartQuote {
        val subtotal = lines.fold(0.0) { sum, line -> sum + line.price.toDouble() }.toBigDecimal()
        val shippingFee = when {
            lines.isEmpty() -> BigDecimal.ZERO
            subtotal >= freeShippingMinimum -> BigDecimal.ZERO
            else -> standardShippingFee
        }
        return CartQuote(subtotal, shippingFee)
    }
}
