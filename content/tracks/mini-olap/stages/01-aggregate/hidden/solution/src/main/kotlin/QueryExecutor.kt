import java.util.PriorityQueue

class QueryExecutor(private val table: Table, private val query: SelectQuery) {
    private val groupColumns: List<Int> = query.groupBy.map(table::indexOf)
    private val aggregateItems: List<AggregateItem> = query.items.filterIsInstance<AggregateItem>().distinct()

    fun run(): List<String> {
        val passedRows = table.rows.filter(::passes)
        val resultRows = summarizeGroups(passedRows)
        val shownRows = orderedRows(resultRows, resultOrder(), query.limit)
        return listOf(query.items.joinToString("|") { it.header }) +
            shownRows.map { row -> row.values.joinToString("|", transform = ::render) }
    }

    private fun passes(row: Row): Boolean = (query.where?.evaluate(row, table) ?: Truth.TRUE) == Truth.TRUE

    private fun summarizeGroups(passedRows: List<Row>): List<ResultRow> {
        val groups = LinkedHashMap<List<Value?>, List<Accumulator>>()
        if (groupColumns.isEmpty()) groups[emptyList()] = newAccumulators()
        for (row in passedRows) {
            val accumulators = groups.getOrPut(groupColumns.map { row[it] }, ::newAccumulators)
            accumulators.forEach { it.add(row) }
        }
        return groups.map { (groupKey, accumulators) -> ResultRow(groupKey, resultValues(groupKey, accumulators)) }
    }

    private fun newAccumulators(): List<Accumulator> = aggregateItems.map { accumulatorFor(it, table) }

    private fun resultValues(groupKey: List<Value?>, accumulators: List<Accumulator>): List<Value?> =
        query.items.map { item ->
            when (item) {
                is ColumnItem -> groupKey[query.groupBy.indexOf(item.column)]
                is AggregateItem -> accumulators[aggregateItems.indexOf(item)].result()
            }
        }

    private fun resultOrder(): Comparator<ResultRow> {
        val keyOrders = query.orderBy.map { key ->
            val position = query.items.indexOf(key.item)
            compareBy(valueOrder(key.descending, key.nullsFirst)) { row: ResultRow -> row.values[position] }
        }
        val defaultOrders = groupColumns.indices.map { position ->
            compareBy(valueOrder(descending = false, nullsFirst = false)) { row: ResultRow -> row.groupKey[position] }
        }
        return (keyOrders + defaultOrders).fold(Comparator { _, _ -> 0 }) { order, next -> order.then(next) }
    }

    private fun valueOrder(descending: Boolean, nullsFirst: Boolean): Comparator<Value?> {
        val direction: Comparator<Value> = if (descending) reverseOrder() else naturalOrder()
        return if (nullsFirst) nullsFirst(direction) else nullsLast(direction)
    }
}

class ResultRow(val groupKey: List<Value?>, val values: List<Value?>)

fun <T> orderedRows(rows: List<T>, order: Comparator<T>, limit: Long?): List<T> {
    if (limit == null || limit >= rows.size) return rows.sortedWith(order)
    val kept = PriorityQueue(limit.toInt() + 1, order.reversed())
    for (row in rows) {
        kept.add(row)
        if (kept.size > limit) kept.poll()
    }
    return kept.sortedWith(order)
}
