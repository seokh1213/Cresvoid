import java.math.BigDecimal

private val HUNDRED = BigDecimal(100)

class Slo(objective: BigDecimal, val total: Selector, val bad: Selector) {
    val errorBudget: BigDecimal = (HUNDRED - objective).divide(HUNDRED)
}

class SloSeries(
    private val errorBudget: BigDecimal,
    private val total: List<CounterSeries>,
    private val bad: List<CounterSeries>,
) {
    fun burnRate(window: RangeWindow): BigDecimal? {
        val totalIncrease = sumOfIncreases(total, window) ?: return null
        val badIncrease = sumOfIncreases(bad, window) ?: return null
        if (totalIncrease.signum() == 0) return null
        return roundedQuotient(badIncrease, totalIncrease * errorBudget)
    }
}
