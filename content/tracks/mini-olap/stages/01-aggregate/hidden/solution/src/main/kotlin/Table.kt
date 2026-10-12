enum class ColumnType {
    INT,
    STRING;

    fun accepts(value: Value?): Boolean = when (this) {
        INT -> value == null || value is IntValue
        STRING -> value == null || value is StringValue
    }
}

data class ColumnDefinition(val name: String, val type: ColumnType)

typealias Row = List<Value?>

class Table(val columns: List<ColumnDefinition>) {
    private val positions: Map<String, Int> = columns.withIndex().associate { (index, column) -> column.name to index }
    private val storedRows = mutableListOf<Row>()

    val rows: List<Row> get() = storedRows

    operator fun contains(column: String): Boolean = column in positions

    fun indexOf(column: String): Int = positions.getValue(column)

    fun typeOf(column: String): ColumnType = columns[indexOf(column)].type

    fun append(rows: List<Row>) {
        storedRows += rows
    }
}
