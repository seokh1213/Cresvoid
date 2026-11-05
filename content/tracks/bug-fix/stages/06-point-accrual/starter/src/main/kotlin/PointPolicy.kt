import java.math.BigDecimal
import java.time.LocalDate

internal class PointRate private constructor(val basisPoints: Int) {
    fun pointsFor(amount: Long): Long {
        val multiplier = basisPoints / 10_000.0
        return kotlin.math.floor(amount * multiplier).toLong()
    }

    fun format(): String = BigDecimal.valueOf(basisPoints.toLong(), 2).toPlainString()

    companion object {
        private val percentFormat = Regex("[0-9]+(?:\\.[0-9]{1,2})?")

        fun parse(percent: String): PointRate? {
            if (!percentFormat.matches(percent)) return null
            val decimal = percent.toBigDecimalOrNull() ?: return null
            if (decimal > BigDecimal("100")) return null
            return PointRate(decimal.movePointRight(2).intValueExact())
        }

        fun capped(basisPoints: Int): PointRate = PointRate(basisPoints.coerceAtMost(10_000))
    }
}

internal data class Promotion(
    val id: String,
    val start: LocalDate,
    val end: LocalDate,
    val bonus: PointRate,
) {
    fun includes(date: LocalDate): Boolean = date >= start && date <= end
}

internal class PointPolicy {
    private val tierRates = mutableMapOf(
        Tier.BASIC to PointRate.capped(100),
        Tier.SILVER to PointRate.capped(300),
        Tier.GOLD to PointRate.capped(500),
    )
    private val promotions = mutableMapOf<String, Promotion>()

    fun setRate(tier: Tier, rate: PointRate) {
        tierRates[tier] = rate
    }

    fun setEvent(promotion: Promotion) {
        promotions[promotion.id] = promotion
    }

    fun rateFor(payment: Payment): PointRate {
        val baseRate = tierRates.getValue(payment.tier).basisPoints
        val eventBonus = promotions.values
            .filter { it.includes(payment.date) }
            .sumOf { it.bonus.basisPoints }
        return PointRate.capped(baseRate + eventBonus)
    }
}
