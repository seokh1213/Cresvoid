import java.util.PriorityQueue

class QueryExecutor(private val source: QuerySource, private val query: SelectQuery) {
    fun run(): List<String> {
        val rows = passedRows()
        val groupsOfSets = query.groupingSets.map { set -> groupRows(rows, set.map(source::column)) }
        val resultRows = ResultRows(query.groupingSets, groupsOfSets)
        val results = query.items.distinct().associateWith { resultColumn(it, rows, groupsOfSets, resultRows) }
        val shownRows = orderedRows(resultRows.count, resultOrder(results, resultRows), query.limit)
        return listOf(query.items.joinToString("|") { it.header }) +
            shownRows.map { row -> query.items.joinToString("|") { results.getValue(it).render(row) } }
    }

    private fun passedRows(): IntArray = query.where?.evaluate(source)?.trueRows?.rows() ?: IntArray(source.rowCount) { it }

    private fun resultColumn(item: SelectItem, rows: IntArray, groupsOfSets: List<Groups>, resultRows: ResultRows): ResultColumn =
        when (item) {
            is ColumnItem -> groupValues(item.column, resultRows)
            is GroupingItem -> {
                val missingFlags = LongArray(resultRows.count) { row -> if (resultRows.isMissing(item.column, row)) 1L else 0L }
                NumberResult(missingFlags)
            }
            is AggregateItem -> {
                val accumulator = source.accumulatorFor(item, resultRows.count)
                groupsOfSets.forEachIndexed { set, groups -> accumulator.accumulate(rows, groups, resultRows.startOfSet(set)) }
                accumulator.result()
            }
        }

    private fun groupValues(columnName: String, resultRows: ResultRows): ResultColumn {
        val column = source.column(columnName)
        return column.resultOf(
            IntArray(resultRows.count) { row ->
                if (resultRows.isMissing(columnName, row)) column.nullCell else column.cells[resultRows.sourceRow(row)]
            },
        )
    }

    private fun resultOrder(results: Map<SelectItem, ResultColumn>, resultRows: ResultRows): Comparator<Int> {
        val keyOrders = query.orderBy.map { key -> rowOrder(results.getValue(key.item), key.descending, key.nullsFirst) }
        val defaultOrders = query.groupColumns.map { column ->
            val missing = BooleanArray(resultRows.count) { resultRows.isMissing(column, it) }
            groupColumnOrder(results[ColumnItem(column)] ?: groupValues(column, resultRows), missing)
        }
        return (keyOrders + defaultOrders).fold(Comparator { _, _ -> 0 }) { order, next -> order.then(next) }
    }

    private fun rowOrder(values: ResultColumn, descending: Boolean, nullsFirst: Boolean): Comparator<Int> =
        Comparator { left, right ->
            val leftIsNull = values.isNull(left)
            val rightIsNull = values.isNull(right)
            when {
                leftIsNull && rightIsNull -> 0
                leftIsNull -> if (nullsFirst) -1 else 1
                rightIsNull -> if (nullsFirst) 1 else -1
                descending -> values.compare(right, left)
                else -> values.compare(left, right)
            }
        }

    private fun groupColumnOrder(values: ResultColumn, missing: BooleanArray): Comparator<Int> {
        fun stateOf(row: Int): GroupKeyState = when {
            missing[row] -> GroupKeyState.MISSING
            values.isNull(row) -> GroupKeyState.NULL
            else -> GroupKeyState.VALUE
        }
        return Comparator { left, right ->
            val leftState = stateOf(left)
            val rightState = stateOf(right)
            when {
                leftState != rightState -> leftState.compareTo(rightState)
                leftState == GroupKeyState.VALUE -> values.compare(left, right)
                else -> 0
            }
        }
    }
}

enum class GroupKeyState {
    VALUE,
    NULL,
    MISSING,
}

class ResultRows(private val sets: List<List<String>>, groupsOfSets: List<Groups>) {
    private val setStarts = groupsOfSets.runningFold(0) { start, groups -> start + groups.count }
    private val setOfRow = IntArray(setStarts.last())
    private val sourceRows = IntArray(setStarts.last())

    val count: Int get() = setStarts.last()

    init {
        groupsOfSets.forEachIndexed { set, groups ->
            for (group in 0 until groups.count) {
                setOfRow[setStarts[set] + group] = set
                sourceRows[setStarts[set] + group] = groups.firstRows[group]
            }
        }
    }

    fun startOfSet(set: Int): Int = setStarts[set]

    fun isMissing(column: String, row: Int): Boolean = column !in sets[setOfRow[row]]

    fun sourceRow(row: Int): Int = sourceRows[row]
}

fun orderedRows(rowCount: Int, order: Comparator<Int>, limit: Long?): List<Int> {
    if (limit == null || limit >= rowCount) return (0 until rowCount).sortedWith(order)
    val kept = PriorityQueue(limit.toInt() + 1, order.reversed())
    for (row in 0 until rowCount) {
        if (kept.size == limit.toInt() && (limit == 0L || order.compare(row, kept.peek()) >= 0)) continue
        kept.add(row)
        if (kept.size > limit) kept.poll()
    }
    return kept.sortedWith(order)
}
