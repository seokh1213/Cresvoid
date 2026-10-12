enum class Truth {
    TRUE,
    FALSE,
    UNKNOWN;

    operator fun not(): Truth = when (this) {
        TRUE -> FALSE
        FALSE -> TRUE
        UNKNOWN -> UNKNOWN
    }

    infix fun and(other: Truth): Truth = when {
        this == FALSE || other == FALSE -> FALSE
        this == UNKNOWN || other == UNKNOWN -> UNKNOWN
        else -> TRUE
    }

    infix fun or(other: Truth): Truth = when {
        this == TRUE || other == TRUE -> TRUE
        this == UNKNOWN || other == UNKNOWN -> UNKNOWN
        else -> FALSE
    }

    companion object {
        fun of(holds: Boolean): Truth = if (holds) TRUE else FALSE
    }
}

fun Condition.evaluate(row: Row, table: Table): Truth = when (this) {
    is Condition.Comparison -> {
        val stored = row[table.indexOf(column)]
        if (stored == null || value == null) Truth.UNKNOWN else Truth.of(operator.holds(stored.compareTo(value)))
    }
    is Condition.IsNull -> Truth.of(row[table.indexOf(column)] == null)
    is Condition.IsNotNull -> Truth.of(row[table.indexOf(column)] != null)
    is Condition.Not -> !operand.evaluate(row, table)
    is Condition.And -> left.evaluate(row, table) and right.evaluate(row, table)
    is Condition.Or -> left.evaluate(row, table) or right.evaluate(row, table)
}
