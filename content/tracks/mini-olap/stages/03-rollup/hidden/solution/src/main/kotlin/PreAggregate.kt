class PreAggregate(
    val name: String,
    val table: Table,
    private val dimensionNames: List<String>,
    measureItems: List<AggregateItem>,
) {
    private val dimensionColumns: Map<String, Column> = dimensionNames.associateWith { table.column(it).emptyCopy() }
    private val measures: Map<AggregateItem, StoredMeasure> = measureItems.associateWith { storedMeasureFor(it, table) }
    private val rowOfDimensions = HashMap<List<Int>, Int>()

    var rowCount = 0
        private set

    val dimensions: Set<String> get() = dimensionColumns.keys

    fun dimension(name: String): Column = dimensionColumns.getValue(name)

    fun hasMeasures(items: Set<AggregateItem>): Boolean = measures.keys.containsAll(items)

    fun storedCounts(item: AggregateItem): LongVector = (measures.getValue(item) as StoredCount).counts

    fun storedSums(item: AggregateItem): LongVector = (measures.getValue(item) as StoredSum).sums

    fun storedExtremes(item: AggregateItem): Column = (measures.getValue(item) as StoredExtreme).extremes

    fun absorb(fromRow: Int, toRow: Int) {
        val rows = IntArray(toRow - fromRow) { fromRow + it }
        val groups = groupRows(rows, dimensionNames.map(table::column))
        val storedRowOfGroup = IntArray(groups.count) { group -> storedRowOf(groups.firstRows[group]) }
        val storedRows = IntArray(rows.size) { position -> storedRowOfGroup[groups.idOfRow[position]] }
        measures.values.forEach { it.absorb(rows, storedRows) }
    }

    private fun storedRowOf(baseRow: Int): Int {
        val dimensionCells = dimensionNames.map { table.column(it).cells[baseRow] }
        return rowOfDimensions.getOrPut(dimensionCells) {
            dimensionNames.forEachIndexed { index, name -> dimensionColumns.getValue(name).append(dimensionCells[index]) }
            measures.values.forEach(StoredMeasure::appendEmpty)
            rowCount++
            rowCount - 1
        }
    }
}

fun requiredMeasures(item: AggregateItem): Set<AggregateItem>? = when (item) {
    CountRows -> setOf(item)
    is CountDistinct -> null
    is Aggregate -> when (item.function) {
        AggregateFunction.AVG -> setOf(
            Aggregate(AggregateFunction.SUM, item.column),
            Aggregate(AggregateFunction.COUNT, item.column),
        )
        else -> setOf(item)
    }
}

fun chooseAggregate(query: SelectQuery, candidates: List<PreAggregate>): PreAggregate? =
    candidates.filter { it.canAnswer(query) }.minWithOrNull(compareBy({ it.rowCount }, { it.name }))

private fun PreAggregate.canAnswer(query: SelectQuery): Boolean {
    val measuresPerItem = query.items.filterIsInstance<AggregateItem>().map { requiredMeasures(it) ?: return false }
    val usedColumns = query.groupColumns + query.where?.columns().orEmpty()
    return dimensions.containsAll(usedColumns) && measuresPerItem.all(::hasMeasures)
}

sealed class StoredMeasure {
    abstract fun appendEmpty()

    abstract fun absorb(baseRows: IntArray, storedRows: IntArray)
}

class StoredCount(private val counted: CountInput) : StoredMeasure() {
    val counts = LongVector(emptyValue = 0)

    override fun appendEmpty() = counts.appendEmpty()

    override fun absorb(baseRows: IntArray, storedRows: IntArray) =
        addCounts(counted, baseRows, storedRows, 0, counts.values)
}

class StoredSum(private val column: IntColumn) : StoredMeasure() {
    val sums = LongVector(emptyValue = NULL_SUM)

    override fun appendEmpty() = sums.appendEmpty()

    override fun absorb(baseRows: IntArray, storedRows: IntArray) =
        addSums(SumInput.Cells(column), baseRows, storedRows, 0, sums.values)
}

class StoredExtreme(private val column: Column, private val keepsSmallest: Boolean) : StoredMeasure() {
    val extremes: Column = column.emptyCopy()

    override fun appendEmpty() = extremes.append(column.nullCell)

    override fun absorb(baseRows: IntArray, storedRows: IntArray) {
        val cells = column.cells
        for (position in baseRows.indices) {
            val cell = cells[baseRows[position]]
            if (cell == column.nullCell) continue
            val storedRow = storedRows[position]
            val current = extremes.cells[storedRow]
            if (current == column.nullCell || isBetter(column.compareCells(cell, current))) extremes[storedRow] = cell
        }
    }

    private fun isBetter(comparison: Int): Boolean = if (keepsSmallest) comparison < 0 else comparison > 0
}

private fun storedMeasureFor(item: AggregateItem, table: Table): StoredMeasure = when (item) {
    CountRows -> StoredCount(CountInput.EveryRow)
    is CountDistinct -> error("COUNT(DISTINCT) 는 측정값이 아니다")
    is Aggregate -> {
        val column = table.column(item.column)
        when (item.function) {
            AggregateFunction.COUNT -> StoredCount(CountInput.NonNullCells(column))
            AggregateFunction.SUM -> StoredSum(column as IntColumn)
            AggregateFunction.MIN -> StoredExtreme(column, keepsSmallest = true)
            AggregateFunction.MAX -> StoredExtreme(column, keepsSmallest = false)
            AggregateFunction.AVG -> error("AVG 는 측정값이 아니다")
        }
    }
}
