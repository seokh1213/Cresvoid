sealed interface MetricKind {
    data object Counter : MetricKind

    data object Gauge : MetricKind

    data class Histogram(val upperBounds: List<Long>) : MetricKind
}

sealed interface Metric {
    class Counter : Metric {
        val series = SeriesRegistry(::CounterSeries)
    }

    class Gauge : Metric {
        val series = SeriesRegistry(::GaugeSeries)
    }

    class Histogram(val upperBounds: List<Long>) : Metric {
        val bucketCount: Int = upperBounds.size + 1
        val series = SeriesRegistry { labels -> HistogramSeries(labels, bucketCount) }
    }

    companion object {
        fun of(kind: MetricKind): Metric = when (kind) {
            MetricKind.Counter -> Counter()
            MetricKind.Gauge -> Gauge()
            is MetricKind.Histogram -> Histogram(kind.upperBounds)
        }
    }
}
