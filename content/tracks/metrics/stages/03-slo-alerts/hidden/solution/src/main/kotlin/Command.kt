import java.math.BigDecimal

sealed interface Command {
    data class DefineMetric(val name: String, val kind: MetricKind) : Command

    data class AddSample(
        val time: Long,
        val valueText: String,
        val metric: String,
        val labels: Set<LabelPair>,
    ) : Command

    data class Increase(val window: RangeWindow, val selector: Selector) : Command

    data class Rate(val window: RangeWindow, val selector: Selector) : Command

    data class Delta(val window: RangeWindow, val selector: Selector) : Command

    data class Buckets(val window: RangeWindow, val selector: Selector) : Command

    data class Quantile(val window: RangeWindow, val level: BigDecimal, val selector: Selector) : Command

    data class DefineSlo(val name: String, val objective: BigDecimal, val total: Selector, val bad: Selector) : Command

    data class Burn(val window: RangeWindow, val slo: String) : Command

    data class DefineAlert(
        val name: String,
        val slo: String,
        val longWindow: Long,
        val shortWindow: Long,
        val threshold: BigDecimal,
    ) : Command

    data class Evaluate(val start: Long, val end: Long, val interval: Long) : Command
}
