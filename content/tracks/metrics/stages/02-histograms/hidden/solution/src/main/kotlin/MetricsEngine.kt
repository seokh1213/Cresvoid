import java.math.BigDecimal

private const val NONE_LINE = "NONE"

class MetricsEngine(private val emit: (String) -> Unit) {
    private val metrics = HashMap<String, Metric>()

    fun handle(line: String) {
        try {
            execute(parseCommand(line))
        } catch (failure: CommandFailure) {
            emit(failure.line)
        }
    }

    private fun execute(command: Command) {
        when (command) {
            is Command.DefineMetric -> defineMetric(command)
            is Command.AddSample -> addSample(command)
            is Command.Increase -> emitValue(increase(command.window, command.selector))
            is Command.Rate -> emitValue(rate(command.window, command.selector))
            is Command.Delta -> emitValue(delta(command.window, command.selector))
            is Command.Buckets -> emit(combinedBuckets(command.window, command.selector)?.line() ?: NONE_LINE)
            is Command.Quantile -> emitValue(quantile(command.window, command.level, command.selector))
        }
    }

    private fun defineMetric(command: Command.DefineMetric) {
        if (command.name in metrics) reject(ErrorCode.EXISTS)
        metrics[command.name] = Metric.of(command.kind)
    }

    private fun addSample(command: Command.AddSample) {
        when (val metric = findMetric(command.metric)) {
            is Metric.Counter -> {
                val value = parseCounterValue(command.valueText)
                metric.series.seriesForNewSample(command.labels, command.time).append(command.time, value)
            }
            is Metric.Gauge -> {
                val value = parseGaugeValue(command.valueText)
                metric.series.seriesForNewSample(command.labels, command.time).append(command.time, value)
            }
            is Metric.Histogram -> {
                val counts = parseBucketCounts(command.valueText, metric.bucketCount)
                metric.series.seriesForNewSample(command.labels, command.time).append(command.time, counts)
            }
        }
    }

    private fun increase(window: RangeWindow, selector: Selector): BigDecimal? {
        val metric = findMetric(selector.metric) as? Metric.Counter ?: reject(ErrorCode.WRONG_TYPE)
        return sumOrNull(metric.series.select(selector.labels).mapNotNull { it.increase(window) })
    }

    private fun rate(window: RangeWindow, selector: Selector): BigDecimal? =
        increase(window, selector)?.let { total -> roundedQuotient(total, BigDecimal.valueOf(window.length)) }

    private fun delta(window: RangeWindow, selector: Selector): BigDecimal? {
        val metric = findMetric(selector.metric) as? Metric.Gauge ?: reject(ErrorCode.WRONG_TYPE)
        return sumOrNull(metric.series.select(selector.labels).mapNotNull { it.delta(window) })
    }

    private fun combinedBuckets(window: RangeWindow, selector: Selector): BucketCounts? {
        val metric = findMetric(selector.metric) as? Metric.Histogram ?: reject(ErrorCode.WRONG_TYPE)
        val increasesPerSeries = metric.series.select(selector.labels).mapNotNull { it.bucketIncreases(window) }
        return BucketCounts.combine(metric.upperBounds, increasesPerSeries)
    }

    private fun quantile(window: RangeWindow, level: BigDecimal, selector: Selector): BigDecimal? =
        combinedBuckets(window, selector)?.let { buckets -> estimateQuantile(level, buckets) }

    private fun findMetric(name: String): Metric = metrics[name] ?: reject(ErrorCode.NO_METRIC)

    private fun emitValue(value: BigDecimal?) {
        emit(if (value == null) NONE_LINE else formatDecimal(value))
    }
}

private fun sumOrNull(values: List<BigDecimal>): BigDecimal? = values.reduceOrNull(BigDecimal::add)
