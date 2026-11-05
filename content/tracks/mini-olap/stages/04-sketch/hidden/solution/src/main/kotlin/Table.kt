enum class ColumnType {
    INT,
    STRING;

    fun accepts(value: Value?): Boolean = when (this) {
        INT -> value == null || value is IntValue
        STRING -> value == null || value is StringValue
    }
}

data class ColumnDefinition(val name: String, val type: ColumnType)

class Table(val definitions: List<ColumnDefinition>) {
    private val positions: Map<String, Int> = definitions.withIndex().associate { (index, column) -> column.name to index }

    val columns: List<Column> = definitions.map { if (it.type == ColumnType.INT) IntColumn() else StringColumn() }

    var rowCount = 0
        private set

    operator fun contains(column: String): Boolean = column in positions

    fun column(name: String): Column = columns[positions.getValue(name)]

    fun typeOf(column: String): ColumnType = definitions[positions.getValue(column)].type

    fun stageRow(cells: IntArray) {
        columns.forEachIndexed { index, column -> column.append(cells[index]) }
    }

    fun commitStagedRows() {
        rowCount = columns.first().size
    }

    fun discardStagedRows() {
        columns.forEach { it.truncate(rowCount) }
    }
}
