sealed interface CopySession {
    fun accept(dataLine: String)

    fun finish(): CommandFailure?
}

class RejectedCopy(private val failure: CommandFailure) : CopySession {
    override fun accept(dataLine: String) {}

    override fun finish(): CommandFailure = failure
}

class CopyLoader(private val table: Table) : CopySession {
    private val cells = IntArray(table.columns.size)
    private var dataLineNumber = 0
    private var failure: CommandFailure? = null

    override fun accept(dataLine: String) {
        dataLineNumber++
        if (failure == null) failure = stage(dataLine)
    }

    override fun finish(): CommandFailure? {
        val rejection = failure
        if (rejection == null) table.commitStagedRows() else table.discardStagedRows()
        return rejection
    }

    private fun stage(line: String): CommandFailure? {
        if (line.count { it == '\t' } + 1 != table.columns.size) {
            return CommandFailure(ErrorCode.VALUE_COUNT, dataLineNumber.toString())
        }
        var start = 0
        for (index in cells.indices) {
            val tab = line.indexOf('\t', start)
            val end = if (tab < 0) line.length else tab
            val cell = table.columns[index].parseCell(line, start, end)
            if (cell == NOT_A_CELL) return CommandFailure(ErrorCode.TYPE, dataLineNumber.toString())
            cells[index] = cell.toInt()
            start = end + 1
        }
        table.stageRow(cells)
        return null
    }
}
