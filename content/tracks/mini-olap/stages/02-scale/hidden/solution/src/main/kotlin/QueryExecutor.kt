import java.util.PriorityQueue

class QueryExecutor(private val table: Table, private val query: SelectQuery) {
    fun run(): List<String> {
        val rows = passedRows()
        val groups = groupRows(rows, query.groupBy.map(table::column))
        val results = query.items.distinct().associateWith { resultColumn(it, rows, groups) }
        val shownRows = orderedRows(groups.count, resultOrder(results, groups), query.limit)
        return listOf(query.items.joinToString("|") { it.header }) +
            shownRows.map { row -> query.items.joinToString("|") { results.getValue(it).render(row) } }
    }

    private fun passedRows(): IntArray = query.where?.evaluate(table)?.trueRows?.rows() ?: IntArray(table.rowCount) { it }

    private fun resultColumn(item: SelectItem, rows: IntArray, groups: Groups): ResultColumn = when (item) {
        is ColumnItem -> groupValues(item.column, groups)
        is AggregateItem -> accumulatorFor(item, table, groups.count).apply { accumulate(rows, groups) }.result()
    }

    private fun groupValues(columnName: String, groups: Groups): ResultColumn {
        val column = table.column(columnName)
        return column.resultOf(IntArray(groups.count) { column.cells[groups.firstRows[it]] })
    }

    private fun resultOrder(results: Map<SelectItem, ResultColumn>, groups: Groups): Comparator<Int> {
        val keyOrders = query.orderBy.map { key -> rowOrder(results.getValue(key.item), key.descending, key.nullsFirst) }
        val defaultOrders = query.groupBy.map { column ->
            rowOrder(results[ColumnItem(column)] ?: groupValues(column, groups), descending = false, nullsFirst = false)
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
