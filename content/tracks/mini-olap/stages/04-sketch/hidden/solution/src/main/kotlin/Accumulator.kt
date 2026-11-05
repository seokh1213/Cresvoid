// 합의 절댓값은 10^16 이하라 Long.MIN_VALUE 를 NULL 표시로 쓸 수 있다
const val NULL_SUM = Long.MIN_VALUE

sealed interface CountInput {
    data object EveryRow : CountInput

    class NonNullCells(val column: Column) : CountInput

    class StoredCounts(val counts: LongVector) : CountInput
}

sealed interface SumInput {
    class Cells(val column: IntColumn) : SumInput

    class StoredSums(val sums: LongVector) : SumInput
}

sealed class Accumulator {
    abstract fun accumulate(rows: IntArray, groups: Groups, offset: Int)

    abstract fun result(): ResultColumn
}

class CountAccumulator(private val input: CountInput, resultRowCount: Int) : Accumulator() {
    private val counts = LongArray(resultRowCount)

    override fun accumulate(rows: IntArray, groups: Groups, offset: Int) {
        addCounts(input, rows, groups.idOfRow, offset, counts)
    }

    override fun result(): ResultColumn = NumberResult(counts)
}

class SumAccumulator(private val input: SumInput, resultRowCount: Int) : Accumulator() {
    private val sums = LongArray(resultRowCount) { NULL_SUM }

    override fun accumulate(rows: IntArray, groups: Groups, offset: Int) {
        addSums(input, rows, groups.idOfRow, offset, sums)
    }

    override fun result(): ResultColumn = NumberResult(sums, BooleanArray(sums.size) { sums[it] == NULL_SUM })
}

class AverageAccumulator(
    private val sumInput: SumInput,
    private val countInput: CountInput,
    resultRowCount: Int,
) : Accumulator() {
    private val sums = LongArray(resultRowCount) { NULL_SUM }
    private val counts = LongArray(resultRowCount)

    override fun accumulate(rows: IntArray, groups: Groups, offset: Int) {
        addSums(sumInput, rows, groups.idOfRow, offset, sums)
        addCounts(countInput, rows, groups.idOfRow, offset, counts)
    }

    override fun result(): ResultColumn = AverageResult(
        LongArray(sums.size) { if (counts[it] == 0L) 0 else averageHundredths(sums[it], counts[it]) },
        BooleanArray(sums.size) { counts[it] == 0L },
    )
}

class ExtremeAccumulator(private val column: Column, resultRowCount: Int, private val keepsSmallest: Boolean) : Accumulator() {
    private val noKey = if (keepsSmallest) Int.MAX_VALUE else Int.MIN_VALUE
    private val bestKeys = IntArray(resultRowCount) { noKey }

    override fun accumulate(rows: IntArray, groups: Groups, offset: Int) {
        when (column) {
            is IntColumn -> keepBest(rows, groups.idOfRow, offset) { number -> number }
            is StringColumn -> {
                val rankOfCode = column.dictionary.order().rankOfCode
                keepBest(rows, groups.idOfRow, offset) { code -> rankOfCode[code] }
            }
        }
    }

    private inline fun keepBest(rows: IntArray, ids: IntArray, offset: Int, orderKeyOf: (cell: Int) -> Int) {
        val cells = column.cells
        for (position in rows.indices) {
            val cell = cells[rows[position]]
            if (cell == column.nullCell) continue
            val resultRow = offset + ids[position]
            val key = orderKeyOf(cell)
            bestKeys[resultRow] = if (keepsSmallest) minOf(bestKeys[resultRow], key) else maxOf(bestKeys[resultRow], key)
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

class DistinctCountAccumulator(private val column: Column, resultRowCount: Int) : Accumulator() {
    private val counts = LongArray(resultRowCount)

    override fun accumulate(rows: IntArray, groups: Groups, offset: Int) {
        val slots = column.groupSlots(rows)
        val slotCount = column.groupSlotCount()
        val seenPairs = KeyIndex.forKeySpace(groups.count * slotCount, rows.size)
        val ids = groups.idOfRow
        for (position in rows.indices) {
            if (slots[position] == NULL_SLOT) continue
            val knownPairs = seenPairs.size
            seenPairs.idOf(ids[position] * slotCount + slots[position])
            if (seenPairs.size > knownPairs) counts[offset + ids[position]]++
        }
    }

    override fun result(): ResultColumn = NumberResult(counts)
}

fun addCounts(input: CountInput, rows: IntArray, ids: IntArray, offset: Int, counts: LongArray) {
    when (input) {
        CountInput.EveryRow -> for (position in rows.indices) counts[offset + ids[position]]++
        is CountInput.NonNullCells -> {
            val cells = input.column.cells
            for (position in rows.indices) {
                if (cells[rows[position]] != input.column.nullCell) counts[offset + ids[position]]++
            }
        }
        is CountInput.StoredCounts -> {
            val stored = input.counts.values
            for (position in rows.indices) counts[offset + ids[position]] += stored[rows[position]]
        }
    }
}

fun addSums(input: SumInput, rows: IntArray, ids: IntArray, offset: Int, sums: LongArray) {
    when (input) {
        is SumInput.Cells -> {
            val cells = input.column.cells
            for (position in rows.indices) {
                val number = cells[rows[position]]
                if (number != NULL_NUMBER) addToSum(sums, offset + ids[position], number.toLong())
            }
        }
        is SumInput.StoredSums -> {
            val stored = input.sums.values
            for (position in rows.indices) {
                val partialSum = stored[rows[position]]
                if (partialSum != NULL_SUM) addToSum(sums, offset + ids[position], partialSum)
            }
        }
    }
}

private fun addToSum(sums: LongArray, row: Int, amount: Long) {
    sums[row] = if (sums[row] == NULL_SUM) amount else sums[row] + amount
}
