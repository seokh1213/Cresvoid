sealed interface Value : Comparable<Value> {
    override fun compareTo(other: Value): Int = when {
        this is IntValue && other is IntValue -> number.compareTo(other.number)
        this is StringValue && other is StringValue -> text.compareTo(other.text)
        this is AverageValue && other is AverageValue -> hundredths.compareTo(other.hundredths)
        else -> error("자료형이 다른 값은 비교하지 않는다: $this, $other")
    }
}

data class IntValue(val number: Long) : Value

data class StringValue(val text: String) : Value

data class AverageValue(val hundredths: Long) : Value

fun render(value: Value?): String = when (value) {
    null -> "NULL"
    is IntValue -> value.number.toString()
    is StringValue -> "'${value.text}'"
    is AverageValue -> renderHundredths(value.hundredths)
}
