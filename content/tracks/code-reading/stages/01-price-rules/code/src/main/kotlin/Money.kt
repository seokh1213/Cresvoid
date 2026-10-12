import java.math.BigDecimal
import java.math.RoundingMode

fun parseWon(text: String): BigDecimal = text.toBigInteger().toBigDecimal()

fun BigDecimal.percentToRate(): BigDecimal = movePointLeft(2)

// 반올림을 거듭해도 오차가 한쪽으로 쌓이지 않게 HALF_EVEN 을 쓴다
fun BigDecimal.roundToWon(): BigDecimal = setScale(0, RoundingMode.HALF_EVEN)

fun BigDecimal.discountAt(rate: BigDecimal): BigDecimal = multiply(rate).roundToWon()

fun BigDecimal.prorate(weights: List<BigDecimal>): List<BigDecimal> {
    val totalWeight = weights.sumOf { it }
    val shares = weights.map { weight -> multiply(weight).divide(totalWeight, 0, RoundingMode.DOWN) }
    val leftoverWon = subtract(shares.sumOf { it }).intValueExact()
    val roundedUp = weights.indices
        .sortedBy { weights[it] }
        .takeLast(leftoverWon)
        .toSet()
    return shares.mapIndexed { index, share -> if (index in roundedUp) share + BigDecimal.ONE else share }
}
