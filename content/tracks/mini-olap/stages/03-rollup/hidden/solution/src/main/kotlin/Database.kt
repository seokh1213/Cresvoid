class Database(private val print: (String) -> Unit) {
    private val tables = mutableMapOf<String, Table>()
    private val aggregates = mutableMapOf<String, PreAggregate>()

    fun execute(line: String) {
        try {
            when (val statement = parseCommand(line)) {
                is Statement.CreateTable -> createTable(statement)
                is Statement.CreateAggregate -> createAggregate(statement)
                is Statement.Insert -> insert(statement)
                is Statement.Select -> select(statement.query).forEach(print)
                is Statement.Explain -> print(explain(statement.query))
            }
        } catch (failure: CommandFailure) {
            print(failure.line)
        }
    }

    fun beginCopy(line: String): CopySession = try {
        CopyLoader(tables[parseCopyTarget(line)] ?: reject(ErrorCode.NO_TABLE))
    } catch (failure: CommandFailure) {
        RejectedCopy(failure)
    }

    fun finishCopy(session: CopySession) {
        when (val outcome = session.finish()) {
            is CopyOutcome.Loaded -> absorbNewRows(outcome.table, outcome.firstNewRow)
            is CopyOutcome.Rejected -> print(outcome.failure.line)
        }
    }

    private fun createTable(statement: Statement.CreateTable) {
        if (isNameTaken(statement.table)) reject(ErrorCode.EXISTS)
        tables[statement.table] = Table(statement.columns)
    }

    private fun createAggregate(statement: Statement.CreateAggregate) {
        if (isNameTaken(statement.name)) reject(ErrorCode.EXISTS)
        val query = statement.query
        val table = tables[query.table] ?: reject(ErrorCode.NO_TABLE)
        checkQuery(query, table)
        val unmergeable = query.items.any { it is CountDistinct || (it is Aggregate && it.function == AggregateFunction.AVG) }
        if (unmergeable) reject(ErrorCode.NOT_MERGEABLE)
        val measures = query.items.filterIsInstance<AggregateItem>().distinct()
        val aggregate = PreAggregate(statement.name, table, query.groupColumns, measures)
        aggregate.absorb(0, table.rowCount)
        aggregates[statement.name] = aggregate
    }

    private fun insert(statement: Statement.Insert) {
        val table = tables[statement.table] ?: reject(ErrorCode.NO_TABLE)
        for (row in statement.rows) {
            if (row.size != table.definitions.size) reject(ErrorCode.VALUE_COUNT)
            if (row.zip(table.definitions).any { (value, column) -> !column.type.accepts(value) }) reject(ErrorCode.TYPE)
        }
        val firstNewRow = table.rowCount
        for (row in statement.rows) table.stageRow(IntArray(row.size) { table.columns[it].encode(row[it]) })
        table.commitStagedRows()
        absorbNewRows(table, firstNewRow)
    }

    private fun select(query: SelectQuery): List<String> {
        val table = tables[query.table] ?: reject(ErrorCode.NO_TABLE)
        checkQuery(query, table)
        val aggregate = chooseAggregate(query, aggregatesOf(table))
        return QueryExecutor(aggregate?.let(::PreAggregateSource) ?: TableSource(table), query).run()
    }

    private fun explain(query: SelectQuery): String {
        val table = tables[query.table] ?: reject(ErrorCode.NO_TABLE)
        checkQuery(query, table)
        val aggregate = chooseAggregate(query, aggregatesOf(table))
        return if (aggregate != null) "AGGREGATE ${aggregate.name}" else "TABLE ${query.table}"
    }

    private fun absorbNewRows(table: Table, firstNewRow: Int) {
        aggregatesOf(table).forEach { it.absorb(firstNewRow, table.rowCount) }
    }

    private fun aggregatesOf(table: Table): List<PreAggregate> = aggregates.values.filter { it.table === table }

    private fun isNameTaken(name: String): Boolean = name in tables || name in aggregates
}
