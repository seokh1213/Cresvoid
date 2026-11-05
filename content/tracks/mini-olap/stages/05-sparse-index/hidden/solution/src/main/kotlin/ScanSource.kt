class ScanSource(private val table: Table, private val candidates: IntArray) : QuerySource {
    private val source = TableSource(table)
    override val rowCount: Int get() = table.rowCount

    override fun column(name: String): Column = table.column(name)

    override fun accumulatorFor(item: AggregateItem, resultRowCount: Int): Accumulator =
        source.accumulatorFor(item, resultRowCount)

    override fun rowsPassing(condition: Condition?): IntArray {
        if (condition == null) return candidates
        val predicate = condition.rowPredicate(table)
        val passed = IntArray(candidates.size)
        var count = 0
        for (row in candidates) if (predicate(row) == Truth.TRUE) passed[count++] = row
        return passed.copyOf(count)
    }
}

private enum class Truth {
    FALSE, UNKNOWN, TRUE;

    operator fun not(): Truth = when (this) {
        TRUE -> FALSE
        FALSE -> TRUE
        UNKNOWN -> UNKNOWN
    }

    infix fun and(other: Truth): Truth = minOf(this, other)

    infix fun or(other: Truth): Truth = maxOf(this, other)
}

private fun Condition.rowPredicate(table: Table): (Int) -> Truth = when (this) {
    is Condition.Comparison -> comparisonPredicate(table.column(column), this)
    is Condition.IsNull -> {
        val source = table.column(column)
        val predicate: (Int) -> Truth = { row -> if (source.cells[row] == source.nullCell) Truth.TRUE else Truth.FALSE }
        predicate
    }
    is Condition.IsNotNull -> {
        val source = table.column(column)
        val predicate: (Int) -> Truth = { row -> if (source.cells[row] != source.nullCell) Truth.TRUE else Truth.FALSE }
        predicate
    }
    is Condition.Not -> {
        val predicate = operand.rowPredicate(table)
        val negated: (Int) -> Truth = { row -> !predicate(row) }
        negated
    }
    is Condition.And -> {
        val leftPredicate = left.rowPredicate(table)
        val rightPredicate = right.rowPredicate(table)
        val conjunction: (Int) -> Truth = { row -> leftPredicate(row) and rightPredicate(row) }
        conjunction
    }
    is Condition.Or -> {
        val leftPredicate = left.rowPredicate(table)
        val rightPredicate = right.rowPredicate(table)
        val disjunction: (Int) -> Truth = { row -> leftPredicate(row) or rightPredicate(row) }
        disjunction
    }
}

private fun comparisonPredicate(column: Column, comparison: Condition.Comparison): (Int) -> Truth {
    val value = comparison.value ?: return { Truth.UNKNOWN }
    val compareCell: (Int) -> Int = when (column) {
        is IntColumn -> { cell -> cell.toLong().compareTo((value as IntValue).number) }
        is StringColumn -> { cell -> column.dictionary.text(cell).compareTo((value as StringValue).text) }
    }
    return { row ->
        val cell = column.cells[row]
        when {
            cell == column.nullCell -> Truth.UNKNOWN
            comparison.operator.holds(compareCell(cell)) -> Truth.TRUE
            else -> Truth.FALSE
        }
    }
}
