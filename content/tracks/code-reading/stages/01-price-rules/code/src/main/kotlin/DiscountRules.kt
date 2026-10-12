import java.math.BigDecimal

enum class MemberGrade(discountPercent: Int) {
    BASIC(0),
    SILVER(3),
    GOLD(5),
    VIP(7),
    ;

    val discountRate: BigDecimal = discountPercent.toBigDecimal().percentToRate()
}

sealed interface Coupon {
    val minimumOrder: BigDecimal

    data class Rate(
        val rate: BigDecimal,
        override val minimumOrder: BigDecimal,
        val maximumDiscount: BigDecimal,
    ) : Coupon

    data class Fixed(val amount: BigDecimal, override val minimumOrder: BigDecimal) : Coupon
}

sealed interface DiscountRule {
    data class GradeDiscount(val rate: BigDecimal) : DiscountRule

    data class BundleDiscount(val group: String, val minimumQuantity: Int, val rate: BigDecimal) : DiscountRule

    data class CouponDiscount(val coupon: Coupon) : DiscountRule
}

fun DiscountRule.applyTo(lines: List<PricedLine>): List<PricedLine> = when (this) {
    is DiscountRule.GradeDiscount -> lines.map { it.discounted("grade", it.payable.discountAt(rate)) }
    is DiscountRule.BundleDiscount -> applyBundle(lines)
    is DiscountRule.CouponDiscount -> applyCoupon(lines)
}

private fun DiscountRule.BundleDiscount.applyBundle(lines: List<PricedLine>): List<PricedLine> {
    val groupQuantity = lines.filter { it.product.bundleGroup == group }.sumOf { it.quantity }
    if (groupQuantity < minimumQuantity) return lines
    return lines.map { line ->
        if (line.product.bundleGroup == group) line.discounted("bundle", line.payable.discountAt(rate)) else line
    }
}

private fun DiscountRule.CouponDiscount.applyCoupon(lines: List<PricedLine>): List<PricedLine> {
    val orderAmount = lines.sumOf { it.payable }
    if (orderAmount < coupon.minimumOrder) return lines
    val discount = when (coupon) {
        is Coupon.Rate -> orderAmount.discountAt(coupon.rate).min(coupon.maximumDiscount)
        is Coupon.Fixed -> coupon.amount.min(orderAmount)
    }
    if (discount.signum() == 0) return lines
    val shares = discount.prorate(lines.map { it.payable })
    return lines.zip(shares) { line, share -> line.discounted("coupon", share) }
}
