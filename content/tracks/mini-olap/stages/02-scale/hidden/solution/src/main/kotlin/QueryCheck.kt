fun checkQuery(query: SelectQuery, table: Table) {
    query.mentionedColumns().firstOrNull { it !in table }?.let { reject(ErrorCode.NO_COLUMN, it) }
    if (!typesMatch(query, table)) reject(ErrorCode.TYPE)
    query.items.filterIsInstance<ColumnItem>()
        .firstOrNull { it.column !in query.groupBy }
        ?.let { reject(ErrorCode.NOT_GROUPED, it.column) }
    if (query.orderBy.any { it.item !in query.items }) reject(ErrorCode.NOT_SELECTED)
}

private fun typesMatch(query: SelectQuery, table: Table): Boolean {
    val numericAggregates = (query.items + query.orderBy.map { it.item })
        .filterIsInstance<Aggregate>()
        .filter { it.function == AggregateFunction.SUM || it.function == AggregateFunction.AVG }
    val comparisons = query.where?.comparisons().orEmpty()
    return numericAggregates.all { table.typeOf(it.column) == ColumnType.INT } &&
        comparisons.all { table.typeOf(it.column).accepts(it.value) }
}
