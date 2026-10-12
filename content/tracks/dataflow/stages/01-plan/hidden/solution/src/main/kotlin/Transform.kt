enum class ValueKind {
    NUMBER,
    LIST,
}

enum class Reducer(val token: String) {
    SUM("sum"),
    MIN("min"),
    MAX("max");

    fun combine(accumulated: Long, value: Long): Long = when (this) {
        SUM -> accumulated + value
        MIN -> minOf(accumulated, value)
        MAX -> maxOf(accumulated, value)
    }
}

enum class Arithmetic(val token: String) {
    ADD("add"),
    MULTIPLY("mul");

    fun apply(value: Long, operand: Long): Long = when (this) {
        ADD -> value + operand
        MULTIPLY -> value * operand
    }
}

enum class Summary(val token: String, val reducer: Reducer?) {
    SUM("sum", Reducer.SUM),
    MIN("min", Reducer.MIN),
    MAX("max", Reducer.MAX),
    COUNT("count", null),
}

enum class Comparison(val symbol: String) {
    EQUAL("="),
    NOT_EQUAL("!="),
    LESS("<"),
    LESS_OR_EQUAL("<="),
    GREATER(">"),
    GREATER_OR_EQUAL(">=");

    fun holds(value: Long, operand: Long): Boolean = when (this) {
        EQUAL -> value == operand
        NOT_EQUAL -> value != operand
        LESS -> value < operand
        LESS_OR_EQUAL -> value <= operand
        GREATER -> value > operand
        GREATER_OR_EQUAL -> value >= operand
    }
}

sealed interface Transform

data class Source(val name: String) : Transform

sealed interface UnaryTransform : Transform {
    val label: String

    fun outputKind(input: ValueKind): ValueKind?
}

data class MapKeyPrefix(val length: Int) : UnaryTransform {
    override val label: String get() = "map"

    override fun outputKind(input: ValueKind): ValueKind = input
}

data class MapValueArithmetic(val arithmetic: Arithmetic, val operand: Long) : UnaryTransform {
    override val label: String get() = "map"

    override fun outputKind(input: ValueKind): ValueKind? = ValueKind.NUMBER.takeIf { input == ValueKind.NUMBER }
}

data class MapValueSummary(val summary: Summary) : UnaryTransform {
    override val label: String get() = "map"

    override fun outputKind(input: ValueKind): ValueKind? = ValueKind.NUMBER.takeIf { input == ValueKind.LIST }
}

data class FilterKeyEquals(val key: String) : UnaryTransform {
    override val label: String get() = "filter"

    override fun outputKind(input: ValueKind): ValueKind = input
}

data class FilterKeyDiffers(val key: String) : UnaryTransform {
    override val label: String get() = "filter"

    override fun outputKind(input: ValueKind): ValueKind = input
}

data class FilterValue(val comparison: Comparison, val operand: Long) : UnaryTransform {
    override val label: String get() = "filter"

    override fun outputKind(input: ValueKind): ValueKind? = ValueKind.NUMBER.takeIf { input == ValueKind.NUMBER }
}

data class ReduceByKey(val reducer: Reducer, val partitions: Int) : UnaryTransform {
    override val label: String get() = "reduceByKey"

    override fun outputKind(input: ValueKind): ValueKind? = ValueKind.NUMBER.takeIf { input == ValueKind.NUMBER }
}

data class GroupByKey(val partitions: Int) : UnaryTransform {
    override val label: String get() = "groupByKey"

    override fun outputKind(input: ValueKind): ValueKind? = ValueKind.LIST.takeIf { input == ValueKind.NUMBER }
}

data class Repartition(val partitions: Int) : UnaryTransform {
    override val label: String get() = "repartition"

    override fun outputKind(input: ValueKind): ValueKind = input
}

data class Coalesce(val partitions: Int) : UnaryTransform {
    override val label: String get() = "coalesce"

    override fun outputKind(input: ValueKind): ValueKind = input
}
