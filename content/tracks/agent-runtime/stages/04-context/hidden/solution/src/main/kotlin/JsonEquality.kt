import java.math.BigDecimal

fun JsonValue.canonicalJson(): String = buildString { appendCanonical(this@canonicalJson) }

fun JsonNumber.toBigDecimal(): BigDecimal = BigDecimal(literal)

fun JsonNumber.isInteger(): Boolean = toBigDecimal().stripTrailingZeros().scale() <= 0

fun JsonNumber.toIntIn(range: IntRange): Int? {
    val value = toBigDecimal()
    if (!isInteger() || value < range.first.toBigDecimal() || value > range.last.toBigDecimal()) return null
    return value.intValueExact()
}

private fun StringBuilder.appendCanonical(value: JsonValue) {
    when (value) {
        // BigDecimal 은 1.0 과 1 을 다른 값으로 보므로 끝의 0 을 지워 같은 수를 같은 글자로 만든다
        is JsonNumber -> append(value.toBigDecimal().stripTrailingZeros().toString())
        is JsonArray -> {
            append('[')
            value.items.forEachIndexed { index, item ->
                if (index > 0) append(',')
                appendCanonical(item)
            }
            append(']')
        }
        is JsonObject -> {
            append('{')
            value.fields.entries.sortedBy { it.key }.forEachIndexed { index, (name, item) ->
                if (index > 0) append(',')
                append(JsonString(name).toJson())
                append(':')
                appendCanonical(item)
            }
            append('}')
        }
        is JsonString, is JsonBoolean, JsonNull -> append(value.toJson())
    }
}
