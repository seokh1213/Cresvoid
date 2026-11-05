sealed interface AxisValue : Comparable<AxisValue> {
    data object BelowAll : AxisValue
    data class Cell(val value: Value?) : AxisValue
    data object AboveAll : AxisValue

    override fun compareTo(other: AxisValue): Int = when {
        this === other -> 0
        this == BelowAll || other == AboveAll -> -1
        this == AboveAll || other == BelowAll -> 1
        this is Cell && other is Cell -> compareNullableValues(value, other.value)
        else -> error("키 경계가 아니다")
    }
}

private fun compareNullableValues(left: Value?, right: Value?): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    left is IntValue && right is IntValue -> left.number.compareTo(right.number)
    left is StringValue && right is StringValue -> left.text.compareTo(right.text)
    else -> error("키 자료형이 다르다")
}

data class KeyPoint(val first: AxisValue, val second: AxisValue) : Comparable<KeyPoint> {
    override fun compareTo(other: KeyPoint): Int {
        val firstOrder = first.compareTo(other.first)
        return if (firstOrder != 0) firstOrder else second.compareTo(other.second)
    }
}

data class Endpoint<T : Comparable<T>>(val value: T, val closed: Boolean)

data class Interval<T : Comparable<T>>(val lower: Endpoint<T>, val upper: Endpoint<T>) {
    val isEmpty: Boolean get() = lower.value > upper.value ||
        (lower.value == upper.value && (!lower.closed || !upper.closed))
    val isPoint: Boolean get() = lower.value == upper.value && lower.closed && upper.closed
}

fun candidateRange(condition: Condition?, keys: List<String>): Interval<KeyPoint> {
    val comparisons = conjunctiveComparisons(condition) ?: return fullKeyRange()
    val first = axisInterval(comparisons.filter { it.column == keys[0] })
    if (first.isEmpty) return emptyKeyRange()
    if (first.isPoint) {
        val second = axisInterval(comparisons.filter { it.column == keys[1] })
        return Interval(
            Endpoint(KeyPoint(first.lower.value, second.lower.value), second.lower.closed),
            Endpoint(KeyPoint(first.upper.value, second.upper.value), second.upper.closed),
        )
    }
    return Interval(
        Endpoint(KeyPoint(first.lower.value, if (first.lower.closed) AxisValue.BelowAll else AxisValue.AboveAll), first.lower.closed),
        Endpoint(KeyPoint(first.upper.value, if (first.upper.closed) AxisValue.AboveAll else AxisValue.BelowAll), first.upper.closed),
    )
}

private fun conjunctiveComparisons(condition: Condition?): List<Condition.Comparison>? = when (condition) {
    null, is Condition.IsNull, is Condition.IsNotNull -> emptyList()
    is Condition.Comparison -> if (condition.value == null || condition.operator == ComparisonOperator.NOT_EQUAL) {
        emptyList()
    } else {
        listOf(condition)
    }
    is Condition.Not, is Condition.Or -> null
    is Condition.And -> {
        val left = conjunctiveComparisons(condition.left)
        val right = conjunctiveComparisons(condition.right)
        if (left == null || right == null) null else left + right
    }
}

private fun axisInterval(comparisons: List<Condition.Comparison>): Interval<AxisValue> {
    var lower: Endpoint<AxisValue> = Endpoint(AxisValue.BelowAll, false)
    var upper: Endpoint<AxisValue> = Endpoint(AxisValue.AboveAll, false)
    for (comparison in comparisons) {
        val value = AxisValue.Cell(comparison.value)
        val operator = comparison.operator
        if (operator in listOf(ComparisonOperator.EQUAL, ComparisonOperator.GREATER, ComparisonOperator.GREATER_OR_EQUAL)) {
            val bound = Endpoint<AxisValue>(value, operator != ComparisonOperator.GREATER)
            if (bound.value > lower.value || (bound.value == lower.value && !bound.closed)) lower = bound
        }
        if (operator in listOf(ComparisonOperator.EQUAL, ComparisonOperator.LESS, ComparisonOperator.LESS_OR_EQUAL)) {
            val bound = Endpoint<AxisValue>(value, operator != ComparisonOperator.LESS)
            if (bound.value < upper.value || (bound.value == upper.value && !bound.closed)) upper = bound
        }
    }
    return Interval(lower, upper)
}

private fun fullKeyRange(): Interval<KeyPoint> = Interval(
    Endpoint(KeyPoint(AxisValue.BelowAll, AxisValue.BelowAll), false),
    Endpoint(KeyPoint(AxisValue.AboveAll, AxisValue.AboveAll), false),
)

private fun emptyKeyRange(): Interval<KeyPoint> = Interval(
    Endpoint(KeyPoint(AxisValue.AboveAll, AxisValue.AboveAll), false),
    Endpoint(KeyPoint(AxisValue.BelowAll, AxisValue.BelowAll), false),
)
