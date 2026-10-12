sealed class Accumulator {
    abstract fun accumulate(rows: IntArray, groups: Groups)

    abstract fun result(): ResultColumn
}

fun accumulatorFor(item: AggregateItem, table: Table, groupCount: Int): Accumulator = when (item) {
    CountRows -> RowCountAccumulator(groupCount)
    is CountDistinct -> DistinctCountAccumulator(table.column(item.column), groupCount)
    is Aggregate -> {
        val column = table.column(item.column)
        when (item.function) {
            AggregateFunction.COUNT -> ValueCountAccumulator(column, groupCount)
            AggregateFunction.SUM -> SumAccumulator(column as IntColumn, groupCount)
            AggregateFunction.AVG -> AverageAccumulator(column as IntColumn, groupCount)
            AggregateFunction.MIN -> ExtremeAccumulator(column, groupCount, keepsSmallest = true)
            AggregateFunction.MAX -> ExtremeAccumulator(column, groupCount, keepsSmallest = false)
        }
    }
}

class RowCountAccumulator(groupCount: Int) : Accumulator() {
    private val counts = LongArray(groupCount)

    override fun accumulate(rows: IntArray, groups: Groups) {
        for (group in groups.idOfRow) counts[group]++
    }

    override fun result(): ResultColumn = NumberResult(counts)
}

class ValueCountAccumulator(private val column: Column, groupCount: Int) : Accumulator() {
    private val counts = LongArray(groupCount)

    override fun accumulate(rows: IntArray, groups: Groups) {
        val cells = column.cells
        val ids = groups.idOfRow
        for (position in rows.indices) {
            if (cells[rows[position]] != column.nullCell) counts[ids[position]]++
        }
    }

    override fun result(): ResultColumn = NumberResult(counts)
}

class SumAccumulator(private val column: IntColumn, groupCount: Int) : Accumulator() {
    private val sums = LongArray(groupCount)
    private val empty = BooleanArray(groupCount) { true }

    override fun accumulate(rows: IntArray, groups: Groups) {
        val cells = column.cells
        val ids = groups.idOfRow
        for (position in rows.indices) {
            val number = cells[rows[position]]
            if (number == NULL_NUMBER) continue
            sums[ids[position]] += number
            empty[ids[position]] = false
        }
    }

    override fun result(): ResultColumn = NumberResult(sums, empty)
}

class AverageAccumulator(private val column: IntColumn, groupCount: Int) : Accumulator() {
    private val sums = LongArray(groupCount)
    private val counts = LongArray(groupCount)

    override fun accumulate(rows: IntArray, groups: Groups) {
        val cells = column.cells
        val ids = groups.idOfRow
        for (position in rows.indices) {
            val number = cells[rows[position]]
            if (number == NULL_NUMBER) continue
            sums[ids[position]] += number
            counts[ids[position]]++
        }
    }

    override fun result(): ResultColumn = AverageResult(
        LongArray(sums.size) { if (counts[it] == 0L) 0 else averageHundredths(sums[it], counts[it]) },
        BooleanArray(sums.size) { counts[it] == 0L },
    )
}

class ExtremeAccumulator(private val column: Column, groupCount: Int, private val keepsSmallest: Boolean) : Accumulator() {
    private val noKey = if (keepsSmallest) Int.MAX_VALUE else Int.MIN_VALUE
    private val bestKeys = IntArray(groupCount) { noKey }

    override fun accumulate(rows: IntArray, groups: Groups) {
        when (column) {
            is IntColumn -> keepBest(rows, groups) { number -> number }
            is StringColumn -> {
                val rankOfCode = column.dictionary.order().rankOfCode
                keepBest(rows, groups) { code -> rankOfCode[code] }
            }
        }
    }

    private inline fun keepBest(rows: IntArray, groups: Groups, orderKeyOf: (cell: Int) -> Int) {
        val cells = column.cells
        val ids = groups.idOfRow
        for (position in rows.indices) {
            val cell = cells[rows[position]]
            if (cell == column.nullCell) continue
            val group = ids[position]
            val key = orderKeyOf(cell)
            bestKeys[group] = if (keepsSmallest) minOf(bestKeys[group], key) else maxOf(bestKeys[group], key)
        }
    }

    override fun result(): ResultColumn {
        val cellOfKey: (Int) -> Int = when (column) {
            is IntColumn -> { number -> number }
            is StringColumn -> {
                val codeOfRank = column.dictionary.order().codeOfRank
                { rank -> codeOfRank[rank] }
            }
        }
        val cells = IntArray(bestKeys.size) { row -> if (bestKeys[row] == noKey) column.nullCell else cellOfKey(bestKeys[row]) }
        return column.resultOf(cells)
    }
}

class DistinctCountAccumulator(private val column: Column, groupCount: Int) : Accumulator() {
    private val counts = LongArray(groupCount)

    override fun accumulate(rows: IntArray, groups: Groups) {
        val slots = column.groupSlots(rows)
        val slotCount = column.groupSlotCount()
        val seenPairs = KeyIndex.forKeySpace(counts.size * slotCount, rows.size)
        val ids = groups.idOfRow
        for (position in rows.indices) {
            if (slots[position] == NULL_SLOT) continue
            val knownPairs = seenPairs.size
            seenPairs.idOf(ids[position] * slotCount + slots[position])
            if (seenPairs.size > knownPairs) counts[ids[position]]++
        }
    }

    override fun result(): ResultColumn = NumberResult(counts)
}
