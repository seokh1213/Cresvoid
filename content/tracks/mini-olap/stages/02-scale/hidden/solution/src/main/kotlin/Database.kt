class Database(private val print: (String) -> Unit) {
    private val tables = mutableMapOf<String, Table>()

    fun execute(line: String) {
        try {
            when (val statement = parseCommand(line)) {
                is Statement.CreateTable -> createTable(statement)
                is Statement.Insert -> insert(statement)
                is Statement.Select -> select(statement.query).forEach(print)
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
        session.finish()?.let { print(it.line) }
    }

    private fun createTable(statement: Statement.CreateTable) {
        if (statement.table in tables) reject(ErrorCode.EXISTS)
        tables[statement.table] = Table(statement.columns)
    }

    private fun insert(statement: Statement.Insert) {
        val table = tables[statement.table] ?: reject(ErrorCode.NO_TABLE)
        for (row in statement.rows) {
            if (row.size != table.definitions.size) reject(ErrorCode.VALUE_COUNT)
            if (row.zip(table.definitions).any { (value, column) -> !column.type.accepts(value) }) reject(ErrorCode.TYPE)
        }
        for (row in statement.rows) table.stageRow(IntArray(row.size) { table.columns[it].encode(row[it]) })
        table.commitStagedRows()
    }

    private fun select(query: SelectQuery): List<String> {
        val table = tables[query.table] ?: reject(ErrorCode.NO_TABLE)
        checkQuery(query, table)
        return QueryExecutor(table, query).run()
    }
}
