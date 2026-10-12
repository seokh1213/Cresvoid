import java.math.BigDecimal

class BurnRateAlert(
    val name: String,
    val slo: Slo,
    private val longWindow: Long,
    private val shortWindow: Long,
    private val threshold: BigDecimal,
) {
    fun conditionMet(series: SloSeries, time: Long): Boolean =
        exceedsThreshold(series.burnRate(RangeWindow(time, longWindow))) &&
            exceedsThreshold(series.burnRate(RangeWindow(time, shortWindow)))

    private fun exceedsThreshold(burnRate: BigDecimal?): Boolean = burnRate != null && burnRate > threshold
}
