import java.math.BigDecimal

data class AppliedDiscount(val label: String, val amount: BigDecimal)

data class PricedLine(
    val product: Product,
    val quantity: Int,
    val discounts: List<AppliedDiscount> = emptyList(),
) {
    val listAmount: BigDecimal = product.price * quantity.toBigDecimal()
    val payable: BigDecimal = listAmount - discounts.sumOf { it.amount }

    fun discounted(label: String, amount: BigDecimal): PricedLine =
        copy(discounts = discounts + AppliedDiscount(label, amount))
}

class Checkout(
    private val catalog: Map<String, Product>,
    private val bundleDiscounts: Map<String, DiscountRule.BundleDiscount>,
) {
    fun price(cart: Cart): List<PricedLine> {
        val listPriced = cart.lines.map { PricedLine(catalog.getValue(it.sku), it.quantity) }
        return rulesFor(cart).fold(listPriced) { lines, rule -> rule.applyTo(lines) }
    }

    private fun rulesFor(cart: Cart): List<DiscountRule> = buildList {
        if (cart.grade.discountRate.signum() != 0) add(DiscountRule.GradeDiscount(cart.grade.discountRate))
        addAll(bundleDiscounts.values)
        cart.coupon?.let { add(DiscountRule.CouponDiscount(it)) }
    }
}
