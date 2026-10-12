sealed interface Statement {
    data class CreateTable(val table: String, val columns: List<ColumnDefinition>) : Statement

    data class CreateAggregate(val name: String, val query: SelectQuery) : Statement

    data class Insert(val table: String, val rows: List<List<Value?>>) : Statement

    data class Select(val query: SelectQuery) : Statement

    data class Explain(val query: SelectQuery) : Statement
}

data class SelectQuery(
    val items: List<SelectItem>,
    val table: String,
    val where: Condition?,
    val groupBy: GroupBy?,
    val orderBy: List<OrderKey>,
    val limit: Long?,
) {
    val groupColumns: List<String> get() = groupBy?.columns.orEmpty()
    val groupingSets: List<List<String>> get() = groupBy?.sets ?: listOf(emptyList())
}

sealed interface GroupBy {
    val columns: List<String>
    val sets: List<List<String>>

    data class ColumnList(override val columns: List<String>) : GroupBy {
        override val sets: List<List<String>> get() = listOf(columns)
    }

    data class GroupingSets(override val sets: List<List<String>>) : GroupBy {
        override val columns: List<String> get() = sets.flatten().distinct()
    }

    data class Rollup(override val columns: List<String>) : GroupBy {
        override val sets: List<List<String>> get() = (columns.size downTo 0).map { columns.take(it) }
    }

    data class Cube(override val columns: List<String>) : GroupBy {
        // mask 의 i 번째 비트가 켜져 있으면 i 번째 열이 그 그룹 집합에 든다
        override val sets: List<List<String>>
            get() = (0 until (1 shl columns.size)).map { mask ->
                columns.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            }
    }
}

sealed interface SelectItem {
    val column: String?
    val header: String
}

data class ColumnItem(override val column: String) : SelectItem {
    override val header: String get() = column
}

data class GroupingItem(override val column: String) : SelectItem {
    override val header: String get() = "GROUPING($column)"
}

sealed interface AggregateItem : SelectItem

data object CountRows : AggregateItem {
    override val column: String? = null
    override val header: String = "COUNT(*)"
}

data class Aggregate(val function: AggregateFunction, override val column: String) : AggregateItem {
    override val header: String get() = "${function.name}($column)"
}

data class CountDistinct(override val column: String) : AggregateItem {
    override val header: String get() = "COUNT(DISTINCT $column)"
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
    items.mapNotNull { it.column } + where?.columns().orEmpty() + groupColumns + orderBy.mapNotNull { it.item.column }

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
