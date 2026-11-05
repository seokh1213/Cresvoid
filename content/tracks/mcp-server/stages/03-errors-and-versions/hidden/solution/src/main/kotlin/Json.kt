sealed interface JsonValue
data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]
    fun contains(key: String): Boolean = key in fields
}
data class JsonArray(val values: List<JsonValue>) : JsonValue
data class JsonString(val value: String) : JsonValue
data class JsonNumber(val literal: String) : JsonValue {
    fun integerOrNull(): Long? {
        val unsigned = literal.removePrefix("-")
        val parts = unsigned.split('e', 'E', limit = 2)
        val mantissa = parts[0]
        val digits = mantissa.replace(".", "").trimStart('0')
        if (digits.isEmpty()) return 0
        val exponent = if (parts.size == 1) 0 else parts[1].toIntOrNull() ?: return null
        val fractionLength = mantissa.substringAfter('.', "").length
        val significant = digits.trimEnd('0')
        val shift = exponent.toLong() - fractionLength + digits.length - significant.length
        if (shift < 0 || significant.length + shift > 16) return null
        val magnitude = (significant + "0".repeat(shift.toInt())).toLong()
        return if (literal.startsWith('-')) -magnitude else magnitude
    }
}
data class JsonBoolean(val value: Boolean) : JsonValue
data object JsonNull : JsonValue

fun obj(vararg fields: Pair<String, JsonValue>): JsonObject = JsonObject(mapOf(*fields))
fun str(value: String): JsonString = JsonString(value)
fun num(value: Long): JsonNumber = JsonNumber(value.toString())
fun arr(vararg values: JsonValue): JsonArray = JsonArray(values.toList())

fun JsonValue.encode(): String = when (this) {
    is JsonObject -> fields.toSortedMap().entries.joinToString(",", "{", "}") { (key, value) ->
        "${str(key).encode()}:${value.encode()}"
    }
    is JsonArray -> values.joinToString(",", "[", "]") { it.encode() }
    is JsonString -> buildString {
        append('"')
        for (character in value) append(when (character) {
            '"' -> "\\\""
            '\\' -> "\\\\"
            '\b' -> "\\b"
            '\u000c' -> "\\f"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> if (character < ' ') "\\u%04x".format(character.code) else character.toString()
        })
        append('"')
    }
    is JsonNumber -> checkNotNull(integerOrNull()).toString()
    is JsonBoolean -> value.toString()
    JsonNull -> "null"
}
