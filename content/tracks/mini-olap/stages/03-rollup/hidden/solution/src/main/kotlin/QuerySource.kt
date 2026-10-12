sealed interface QuerySource {
    val rowCount: Int

    fun column(name: String): Column

    fun accumulatorFor(item: AggregateItem, resultRowCount: Int): Accumulator
}

class TableSource(private val table: Table) : QuerySource {
    override val rowCount: Int get() = table.rowCount

    override fun column(name: String): Column = table.column(name)

    override fun accumulatorFor(item: AggregateItem, resultRowCount: Int): Accumulator = when (item) {
        CountRows -> CountAccumulator(CountInput.EveryRow, resultRowCount)
        is CountDistinct -> DistinctCountAccumulator(table.column(item.column), resultRowCount)
        is Aggregate -> {
            val column = table.column(item.column)
            when (item.function) {
                AggregateFunction.COUNT -> CountAccumulator(CountInput.NonNullCells(column), resultRowCount)
                AggregateFunction.SUM -> SumAccumulator(SumInput.Cells(column as IntColumn), resultRowCount)
                AggregateFunction.AVG ->
                    AverageAccumulator(SumInput.Cells(column as IntColumn), CountInput.NonNullCells(column), resultRowCount)
                AggregateFunction.MIN -> ExtremeAccumulator(column, resultRowCount, keepsSmallest = true)
                AggregateFunction.MAX -> ExtremeAccumulator(column, resultRowCount, keepsSmallest = false)
            }
        }
    }
}

class PreAggregateSource(private val aggregate: PreAggregate) : QuerySource {
    override val rowCount: Int get() = aggregate.rowCount

    override fun column(name: String): Column = aggregate.dimension(name)

    override fun accumulatorFor(item: AggregateItem, resultRowCount: Int): Accumulator = when (item) {
        CountRows -> CountAccumulator(CountInput.StoredCounts(aggregate.storedCounts(item)), resultRowCount)
        is CountDistinct -> error("사전 집계로 답하는 질의에는 COUNT(DISTINCT) 가 없다")
        is Aggregate -> when (item.function) {
            AggregateFunction.COUNT -> CountAccumulator(CountInput.StoredCounts(aggregate.storedCounts(item)), resultRowCount)
            AggregateFunction.SUM -> SumAccumulator(SumInput.StoredSums(aggregate.storedSums(item)), resultRowCount)
            AggregateFunction.AVG -> AverageAccumulator(
                SumInput.StoredSums(aggregate.storedSums(Aggregate(AggregateFunction.SUM, item.column))),
                CountInput.StoredCounts(aggregate.storedCounts(Aggregate(AggregateFunction.COUNT, item.column))),
                resultRowCount,
            )
            AggregateFunction.MIN -> ExtremeAccumulator(aggregate.storedExtremes(item), resultRowCount, keepsSmallest = true)
            AggregateFunction.MAX -> ExtremeAccumulator(aggregate.storedExtremes(item), resultRowCount, keepsSmallest = false)
        }
    }
}
