enum class MetricKind {
    COUNTER,
    GAUGE,
}

sealed interface Metric {
    class Counter : Metric {
        val series = SeriesRegistry(::CounterSeries)
    }

    class Gauge : Metric {
        val series = SeriesRegistry(::GaugeSeries)
    }

    companion object {
        fun of(kind: MetricKind): Metric = when (kind) {
            MetricKind.COUNTER -> Counter()
            MetricKind.GAUGE -> Gauge()
        }
    }
}
