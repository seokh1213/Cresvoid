package cresvoid.json

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val integerLiteral = Regex("-?(0|[1-9][0-9]*)")

class JsonFields(
    private val fields: Map<String, JsonValue>,
    private val location: String,
    private val strict: Boolean,
) {
    private val read = mutableSetOf<String>()

    fun string(name: String): String = optionalString(name) ?: throw missing(name)

    fun optionalString(name: String): String? = take(name)?.let { value ->
        (value as? JsonString)?.value ?: throw wrongType(pathOf(name), "문자열이 아닙니다")
    }

    fun optionalInt(name: String): Int? = take(name)?.let { value -> value.asInt() ?: throw wrongType(pathOf(name), "정수가 아닙니다") }

    fun optionalDouble(name: String): Double? = take(name)?.let { value ->
        (value as? JsonNumber)?.literal?.toDouble()?.takeIf { it.isFinite() } ?: throw wrongType(pathOf(name), "수가 아닙니다")
    }

    fun optionalTime(name: String): OffsetDateTime? = optionalString(name)?.let { text ->
        try {
            OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (_: DateTimeParseException) {
            throw wrongType(pathOf(name), "RFC 3339 시각이 아닙니다")
        }
    }

    fun stringList(name: String): List<String> = optionalStringList(name) ?: throw missing(name)

    fun optionalStringList(name: String): List<String>? = take(name)?.let { value ->
        val array = value as? JsonArray ?: throw wrongType(pathOf(name), "배열이 아닙니다")
        array.items.mapIndexed { index, item ->
            (item as? JsonString)?.value ?: throw wrongType("${pathOf(name)}[$index]", "문자열이 아닙니다")
        }
    }

    fun <T> objectList(name: String, decode: (JsonFields) -> T): List<T> {
        val value = take(name) ?: throw missing(name)
        val array = value as? JsonArray ?: throw wrongType(pathOf(name), "배열이 아닙니다")
        return array.items.mapIndexed { index, item -> decodeJsonObject(item, "${pathOf(name)}[$index]", strict, decode) }
    }

    fun <T> optionalObject(name: String, decode: (JsonFields) -> T): T? =
        take(name)?.let { value -> decodeJsonObject(value, pathOf(name), strict, decode) }

    fun <T> optionalObjectMap(name: String, decode: (JsonFields) -> T): Map<String, T>? = optionalMap(name) { key, item ->
        decodeJsonObject(item, "${pathOf(name)}.$key", strict, decode)
    }

    fun optionalIntMap(name: String): Map<String, Int>? = optionalMap(name) { key, item ->
        item.asInt() ?: throw wrongType("${pathOf(name)}.$key", "정수가 아닙니다")
    }

    fun rejectUnknownFields() {
        if (!strict) return
        val unknown = fields.keys.firstOrNull { it !in read } ?: return
        throw JsonError("모르는 필드입니다: ${pathOf(unknown)}")
    }

    private fun <T> optionalMap(name: String, decodeEntry: (String, JsonValue) -> T): Map<String, T>? = take(name)?.let { value ->
        val entries = (value as? JsonObject)?.fields ?: throw wrongType(pathOf(name), "객체가 아닙니다")
        entries.filterValues { strict || it != JsonNull }.mapValues { (key, item) -> decodeEntry(key, item) }
    }

    private fun take(name: String): JsonValue? {
        read += name
        val value = fields[name]
        return if (!strict && value == JsonNull) null else value
    }

    private fun pathOf(name: String): String = if (location.isEmpty()) name else "$location.$name"

    private fun missing(name: String) = JsonError("필수 필드가 없습니다: ${pathOf(name)}")
}

private fun wrongType(path: String, problem: String) = JsonError("$problem: $path")

private fun JsonValue.asInt(): Int? = (this as? JsonNumber)?.literal?.takeIf { integerLiteral.matches(it) }?.toIntOrNull()

fun <T> decodeJsonObject(value: JsonValue, location: String, strict: Boolean, decode: (JsonFields) -> T): T {
    val entries = (value as? JsonObject)?.fields ?: throw wrongType(location.ifEmpty { "최상위 값" }, "객체가 아닙니다")
    val fields = JsonFields(entries, location, strict)
    return decode(fields).also { fields.rejectUnknownFields() }
}

