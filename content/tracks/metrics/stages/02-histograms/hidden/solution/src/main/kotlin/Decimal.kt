import java.math.BigDecimal
import java.math.RoundingMode

private const val FRACTION_DIGITS = 6

// HALF_UP 은 한가운데 값을 0 에서 먼 쪽으로 올린다
fun roundedQuotient(dividend: BigDecimal, divisor: BigDecimal): BigDecimal =
    dividend.divide(divisor, FRACTION_DIGITS, RoundingMode.HALF_UP)

fun formatDecimal(value: BigDecimal): String = value.setScale(FRACTION_DIGITS).toPlainString()
