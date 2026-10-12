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
}
