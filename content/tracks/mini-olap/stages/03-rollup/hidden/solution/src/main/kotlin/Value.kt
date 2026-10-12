sealed interface Value

data class IntValue(val number: Long) : Value

data class StringValue(val text: String) : Value
