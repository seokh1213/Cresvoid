private val COMMANDS = setOf("CREATE", "INSERT", "SELECT", "EXPLAIN")
private val FUNCTIONS = AggregateFunction.entries.associateBy { it.name }

fun parseCommand(line: String): Statement {
    if (line.substringBefore(' ') !in COMMANDS) reject(ErrorCode.UNKNOWN_COMMAND)
    val cursor = TokenCursor(tokenize(line) ?: syntaxError())
    val statement = when {
        cursor.acceptKeyword("CREATE") -> when {
            cursor.acceptKeyword("AGGREGATE") -> parseCreateAggregate(cursor)
            else -> parseCreateTable(cursor)
        }
        cursor.acceptKeyword("INSERT") -> parseInsert(cursor)
        cursor.acceptKeyword("SELECT") -> Statement.Select(parseSelect(cursor))
        cursor.acceptKeyword("EXPLAIN") -> {
            cursor.expectKeyword("SELECT")
            Statement.Explain(parseSelect(cursor))
        }
        else -> syntaxError()
    }
    cursor.expectEnd()
    return statement
}

fun parseCopyTarget(line: String): String {
    val cursor = TokenCursor(tokenize(line) ?: syntaxError())
    cursor.expectKeyword("COPY")
    val table = cursor.name()
    cursor.expectKeyword("FROM")
    cursor.expectKeyword("STDIN")
    cursor.expectEnd()
    return table
}

private fun parseCreateTable(cursor: TokenCursor): Statement.CreateTable {
    cursor.expectKeyword("TABLE")
    val table = cursor.name()
    cursor.expectSymbol("(")
    val columns = cursor.commaSeparated { ColumnDefinition(cursor.name(), parseColumnType(cursor)) }
    cursor.expectSymbol(")")
    if (columns.distinctBy { it.name }.size != columns.size) syntaxError()
    return Statement.CreateTable(table, columns)
}

private fun parseCreateAggregate(cursor: TokenCursor): Statement.CreateAggregate {
    val name = cursor.name()
    cursor.expectKeyword("AS")
    cursor.expectKeyword("SELECT")
    val query = parseSelect(cursor)
    val definesGroups = query.groupBy is GroupBy.ColumnList && query.items.none { it is GroupingItem }
    if (!definesGroups || query.where != null || query.orderBy.isNotEmpty() || query.limit != null) syntaxError()
    return Statement.CreateAggregate(name, query)
}

private fun parseColumnType(cursor: TokenCursor): ColumnType = when {
    cursor.acceptKeyword("INT") -> ColumnType.INT
    cursor.acceptKeyword("STRING") -> ColumnType.STRING
    else -> syntaxError()
}

private fun parseInsert(cursor: TokenCursor): Statement.Insert {
    cursor.expectKeyword("INTO")
    val table = cursor.name()
    cursor.expectKeyword("VALUES")
    val rows = cursor.commaSeparated {
        cursor.expectSymbol("(")
        val values = cursor.commaSeparated { cursor.value() }
        cursor.expectSymbol(")")
        values
    }
    return Statement.Insert(table, rows)
}

private fun parseSelect(cursor: TokenCursor): SelectQuery {
    val items = cursor.commaSeparated { parseItem(cursor) }
    cursor.expectKeyword("FROM")
    val table = cursor.name()
    val where = if (cursor.acceptKeyword("WHERE")) parseCondition(cursor) else null
    val groupBy = if (cursor.acceptKeyword("GROUP")) parseGroupBy(cursor) else null
    val orderBy = if (cursor.acceptKeyword("ORDER")) parseOrderBy(cursor) else emptyList()
    val limit = if (cursor.acceptKeyword("LIMIT")) parseLimit(cursor) else null
    return SelectQuery(items, table, where, groupBy, orderBy, limit)
}

private fun parseLimit(cursor: TokenCursor): Long {
    val limit = cursor.integer()
    if (limit < 0) syntaxError()
    return limit
}

private fun parseItem(cursor: TokenCursor): SelectItem {
    if (cursor.acceptKeyword("GROUPING")) return GroupingItem(parseParenthesizedName(cursor))
    val function = cursor.nextWord()?.let { FUNCTIONS[it] } ?: return ColumnItem(cursor.name())
    cursor.skip()
    cursor.expectSymbol("(")
    val item = when {
        function == AggregateFunction.COUNT && cursor.acceptSymbol("*") -> CountRows
        function == AggregateFunction.COUNT && cursor.acceptKeyword("DISTINCT") -> CountDistinct(cursor.name())
        else -> Aggregate(function, cursor.name())
    }
    cursor.expectSymbol(")")
    return item
}

private fun parseParenthesizedName(cursor: TokenCursor): String {
    cursor.expectSymbol("(")
    val name = cursor.name()
    cursor.expectSymbol(")")
    return name
}

private fun parseGroupBy(cursor: TokenCursor): GroupBy {
    cursor.expectKeyword("BY")
    return when {
        cursor.acceptKeyword("GROUPING") -> {
            cursor.expectKeyword("SETS")
            cursor.expectSymbol("(")
            val sets = cursor.commaSeparated { parseGroupingSet(cursor) }
            cursor.expectSymbol(")")
            if (sets.map { it.toSet() }.distinct().size != sets.size) syntaxError()
            GroupBy.GroupingSets(sets)
        }
        cursor.acceptKeyword("ROLLUP") -> GroupBy.Rollup(parseColumnList(cursor))
        cursor.acceptKeyword("CUBE") -> GroupBy.Cube(parseColumnList(cursor))
        else -> GroupBy.ColumnList(distinctColumns(cursor.commaSeparated { cursor.name() }))
    }
}

private fun parseGroupingSet(cursor: TokenCursor): List<String> {
    if (!cursor.acceptSymbol("(")) return listOf(cursor.name())
    if (cursor.acceptSymbol(")")) return emptyList()
    return parseColumnsToClosing(cursor)
}

private fun parseColumnList(cursor: TokenCursor): List<String> {
    cursor.expectSymbol("(")
    return parseColumnsToClosing(cursor)
}

private fun parseColumnsToClosing(cursor: TokenCursor): List<String> {
    val columns = distinctColumns(cursor.commaSeparated { cursor.name() })
    cursor.expectSymbol(")")
    return columns
}

private fun distinctColumns(columns: List<String>): List<String> {
    if (columns.distinct().size != columns.size) syntaxError()
    return columns
}

private fun parseOrderBy(cursor: TokenCursor): List<OrderKey> {
    cursor.expectKeyword("BY")
    return cursor.commaSeparated {
        val item = parseItem(cursor)
        val descending = parseDescending(cursor)
        val nullsFirst = if (cursor.acceptKeyword("NULLS")) parseNullsFirst(cursor) else descending
        OrderKey(item, descending, nullsFirst)
    }
}

private fun parseDescending(cursor: TokenCursor): Boolean {
    if (cursor.acceptKeyword("DESC")) return true
    cursor.acceptKeyword("ASC")
    return false
}

private fun parseNullsFirst(cursor: TokenCursor): Boolean = when {
    cursor.acceptKeyword("FIRST") -> true
    cursor.acceptKeyword("LAST") -> false
    else -> syntaxError()
}

private fun parseCondition(cursor: TokenCursor): Condition {
    var condition = parseConjunction(cursor)
    while (cursor.acceptKeyword("OR")) condition = Condition.Or(condition, parseConjunction(cursor))
    return condition
}

private fun parseConjunction(cursor: TokenCursor): Condition {
    var condition = parseNegation(cursor)
    while (cursor.acceptKeyword("AND")) condition = Condition.And(condition, parseNegation(cursor))
    return condition
}

private fun parseNegation(cursor: TokenCursor): Condition = when {
    cursor.acceptKeyword("NOT") -> Condition.Not(parseNegation(cursor))
    cursor.acceptSymbol("(") -> parseCondition(cursor).also { cursor.expectSymbol(")") }
    else -> parseComparison(cursor)
}

private fun parseComparison(cursor: TokenCursor): Condition {
    val column = cursor.name()
    if (!cursor.acceptKeyword("IS")) return Condition.Comparison(column, cursor.comparisonOperator(), cursor.value())
    val negated = cursor.acceptKeyword("NOT")
    cursor.expectKeyword("NULL")
    return if (negated) Condition.IsNotNull(column) else Condition.IsNull(column)
}
