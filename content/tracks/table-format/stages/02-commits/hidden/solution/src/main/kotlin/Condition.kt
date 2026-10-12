enum class Operator(val symbol: String) {
    EQ("="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">="),
    ;

    fun holds(comparison: Int): Boolean = when (this) {
        EQ -> comparison == 0
        LT -> comparison < 0
        LE -> comparison <= 0
        GT -> comparison > 0
        GE -> comparison >= 0
    }
}

data class Condition(val column: Int, val operator: Operator, val value: Value) {
    fun matches(row: Row): Boolean = operator.holds(row[column].compareTo(value))

    fun excludes(file: DataFile): Boolean {
        val lower = file.lowerBounds[column]
        val upper = file.upperBounds[column]
        return when (operator) {
            Operator.EQ -> value < lower || value > upper
            Operator.LT -> lower >= value
            Operator.LE -> lower > value
            Operator.GT -> upper <= value
            Operator.GE -> upper < value
        }
    }
}
