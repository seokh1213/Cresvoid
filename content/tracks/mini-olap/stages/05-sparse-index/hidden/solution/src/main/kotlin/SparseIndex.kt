data class SortingDefinition(val columns: List<String>, val granularity: Long)

data class GranuleSelection(val rows: IntArray, val granules: Int)

class SparseIndex(private val table: Table, private val sorting: SortingDefinition) {
    private var sortedRows = IntArray(0)
    private var marks = emptyList<KeyPoint>()
    private val granularity = sorting.granularity.toInt()

    fun select(condition: Condition?): GranuleSelection {
        refresh()
        val range = candidateRange(condition, sorting.columns)
        if (range.isEmpty || marks.isEmpty()) return GranuleSelection(IntArray(0), 0)
        val firstMark = firstMarkWhere { mark ->
            mark > range.lower.value || (mark == range.lower.value && range.lower.closed)
        }
        val firstGranule = maxOf(0, firstMark - 1)
        val pastLastGranule = firstMarkWhere { mark ->
            mark > range.upper.value || (mark == range.upper.value && !range.upper.closed)
        }
        if (pastLastGranule <= firstGranule) return GranuleSelection(IntArray(0), 0)
        val from = firstGranule * granularity
        val until = minOf(pastLastGranule * granularity, sortedRows.size)
        return GranuleSelection(sortedRows.copyOfRange(from, until), pastLastGranule - firstGranule)
    }

    private fun refresh() {
        if (sortedRows.size == table.rowCount) return
        val keys = sorting.columns.map(table::column)
        sortedRows = sortRowIds(table.rowCount) { left, right ->
            val firstOrder = compareCells(keys[0], left, right)
            if (firstOrder != 0) firstOrder else compareCells(keys[1], left, right)
        }
        marks = (sortedRows.indices step granularity).map { position ->
            val row = sortedRows[position]
            KeyPoint(axisValue(keys[0], row), axisValue(keys[1], row))
        }
    }

    private fun firstMarkWhere(predicate: (KeyPoint) -> Boolean): Int {
        var low = 0
        var high = marks.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (predicate(marks[middle])) high = middle else low = middle + 1
        }
        return low
    }

    private fun compareCells(column: Column, left: Int, right: Int): Int {
        val leftCell = column.cells[left]
        val rightCell = column.cells[right]
        return when {
            leftCell == rightCell -> 0
            leftCell == column.nullCell -> 1
            rightCell == column.nullCell -> -1
            else -> column.compareCells(leftCell, rightCell)
        }
    }

    private fun axisValue(column: Column, row: Int): AxisValue.Cell {
        val cell = column.cells[row]
        return AxisValue.Cell(when {
            cell == column.nullCell -> null
            column is IntColumn -> IntValue(cell.toLong())
            column is StringColumn -> StringValue(column.dictionary.text(cell))
            else -> error("키 열 자료형이 없다")
        })
    }
}

// 같은 키는 왼쪽 행을 먼저 취해 적재 순서를 보존하고 행 번호를 박싱하지 않는다.
fun sortRowIds(count: Int, compare: (Int, Int) -> Int): IntArray {
    var rows = IntArray(count) { it }
    var merged = IntArray(count)
    var width = 1
    while (width < count) {
        var start = 0
        while (start < count) {
            val middle = minOf(start + width, count)
            val end = minOf(start + 2 * width, count)
            var left = start
            var right = middle
            for (position in start until end) {
                if (left < middle && (right == end || compare(rows[left], rows[right]) <= 0)) {
                    merged[position] = rows[left++]
                } else {
                    merged[position] = rows[right++]
                }
            }
            start = end
        }
        val previous = rows
        rows = merged
        merged = previous
        width *= 2
    }
    return rows
}
