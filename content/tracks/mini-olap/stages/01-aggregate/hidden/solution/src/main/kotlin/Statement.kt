sealed interface Statement {
    data class CreateTable(val table: String, val columns: List<ColumnDefinition>) : Statement

    data class Insert(val table: String, val rows: List<List<Value?>>) : Statement

    data class Select(val query: SelectQuery) : Statement
}

data class SelectQuery(
    val items: List<SelectItem>,
    val table: String,
    val where: Condition?,
    val groupBy: List<String>,
    val orderBy: List<OrderKey>,
    val limit: Long?,
)

sealed interface SelectItem {
    val column: String?
    val header: String
}

data class ColumnItem(override val column: String) : SelectItem {
    override val header: String get() = column
}

sealed interface AggregateItem : SelectItem

data object CountRows : AggregateItem {
    override val column: String? = null
    override val header: String = "COUNT(*)"
}

data class Aggregate(val function: AggregateFunction, override val column: String) : AggregateItem {
    override val header: String get() = "${function.name}($column)"
}

enum class AggregateFunction {
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,
}

data class OrderKey(val item: SelectItem, val descending: Boolean, val nullsFirst: Boolean)

sealed interface Condition {
    data class Comparison(val column: String, val operator: ComparisonOperator, val value: Value?) : Condition

    data class IsNull(val column: String) : Condition

    data class IsNotNull(val column: String) : Condition

    data class Not(val operand: Condition) : Condition

    data class And(val left: Condition, val right: Condition) : Condition

    data class Or(val left: Condition, val right: Condition) : Condition
}

enum class ComparisonOperator(val symbol: String) {
    EQUAL("="),
    NOT_EQUAL("<>"),
    LESS("<"),
    LESS_OR_EQUAL("<="),
    GREATER(">"),
    GREATER_OR_EQUAL(">=");

    fun holds(comparison: Int): Boolean = when (this) {
        EQUAL -> comparison == 0
        NOT_EQUAL -> comparison != 0
        LESS -> comparison < 0
        LESS_OR_EQUAL -> comparison <= 0
        GREATER -> comparison > 0
        GREATER_OR_EQUAL -> comparison >= 0
    }
}

fun SelectQuery.mentionedColumns(): List<String> =
    items.mapNotNull { it.column } + where?.columns().orEmpty() + groupBy + orderBy.mapNotNull { it.item.column }

fun Condition.columns(): List<String> = when (this) {
    is Condition.Comparison -> listOf(column)
    is Condition.IsNull -> listOf(column)
    is Condition.IsNotNull -> listOf(column)
    is Condition.Not -> operand.columns()
    is Condition.And -> left.columns() + right.columns()
    is Condition.Or -> left.columns() + right.columns()
}

fun Condition.comparisons(): List<Condition.Comparison> = when (this) {
    is Condition.Comparison -> listOf(this)
    is Condition.IsNull, is Condition.IsNotNull -> emptyList()
    is Condition.Not -> operand.comparisons()
    is Condition.And -> left.comparisons() + right.comparisons()
    is Condition.Or -> left.comparisons() + right.comparisons()
}
