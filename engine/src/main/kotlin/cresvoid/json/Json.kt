package cresvoid.json

sealed interface JsonValue

data class JsonString(val value: String) : JsonValue

data class JsonNumber(val literal: String) : JsonValue

data class JsonBoolean(val value: Boolean) : JsonValue

data object JsonNull : JsonValue

data class JsonArray(val items: List<JsonValue>) : JsonValue

data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue

class JsonError(override val message: String) : Exception(message)

enum class KeyRule { SNAKE_CASE_UNIQUE, ANY }

class ParsedJson(val value: JsonValue, val hasTrailingContent: Boolean)

private val snakeCaseKey = Regex("[a-z][a-z0-9_]*")

fun parseJson(text: String, keyRule: KeyRule): ParsedJson = JsonParser(text, keyRule).parseDocument()

private class JsonParser(private val text: String, private val keyRule: KeyRule) {
    private var position = 0

    fun parseDocument(): ParsedJson {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        return ParsedJson(value, hasTrailingContent = position < text.length)
    }

    private fun parseValue(): JsonValue {
        val char = text.getOrNull(position) ?: throw endOfInput()
        return when {
            char == '{' -> parseObject()
            char == '[' -> parseArray()
            char == '"' -> JsonString(parseString())
            char == 't' -> parseLiteral("true", JsonBoolean(true))
            char == 'f' -> parseLiteral("false", JsonBoolean(false))
            char == 'n' -> parseLiteral("null", JsonNull)
            char == '-' || char in '0'..'9' -> parseNumber()
            else -> throw unexpected()
        }
    }

    private fun parseObject(): JsonObject {
        position++
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (consume('}')) return JsonObject(fields)
        while (true) {
            skipWhitespace()
            if (text.getOrNull(position) != '"') throw unexpectedOrEnd()
            val key = parseString()
            checkKey(key, fields)
            skipWhitespace()
            if (!consume(':')) throw unexpectedOrEnd()
            skipWhitespace()
            fields[key] = parseValue()
            skipWhitespace()
            if (consume('}')) return JsonObject(fields)
            if (!consume(',')) throw unexpectedOrEnd()
        }
    }

    private fun checkKey(key: String, fields: Map<String, JsonValue>) {
        if (keyRule == KeyRule.ANY) return
        if (!snakeCaseKey.matches(key)) throw JsonError("키는 영문 소문자 snake_case 여야 합니다: \"$key\"")
        if (key in fields) throw JsonError("키가 겹칩니다: \"$key\"")
    }

    private fun parseArray(): JsonArray {
        position++
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (consume(']')) return JsonArray(items)
        while (true) {
            skipWhitespace()
            items += parseValue()
            skipWhitespace()
            if (consume(']')) return JsonArray(items)
            if (!consume(',')) throw unexpectedOrEnd()
        }
    }

    private fun parseString(): String {
        position++
        val builder = StringBuilder()
        while (true) {
            val char = text.getOrNull(position) ?: throw endOfInput()
            when {
                char == '"' -> {
                    position++
                    return builder.toString()
                }
                char == '\\' -> {
                    position++
                    appendEscape(builder)
                }
                char < ' ' -> throw unexpected()
                else -> {
                    builder.append(char)
                    position++
                }
            }
        }
    }

    private fun appendEscape(builder: StringBuilder) {
        val escape = text.getOrNull(position) ?: throw endOfInput()
        val replacement = when (escape) {
            '"' -> "\""
            '\\' -> "\\"
            '/' -> "/"
            'b' -> "\b"
            'f' -> "\u000C"
            'n' -> "\n"
            'r' -> "\r"
            't' -> "\t"
            'u' -> null
            else -> throw unexpected()
        }
        position++
        builder.append(replacement ?: readUnicodeEscape())
    }

    private fun readUnicodeEscape(): String {
        val first = readHex4()
        if (first in 0xD800..0xDBFF && text.startsWith("\\u", position)) {
            val afterFirst = position
            position += 2
            val second = readHex4()
            if (second in 0xDC00..0xDFFF) return "${first.toChar()}${second.toChar()}"
            position = afterFirst
        }
        return if (first in 0xD800..0xDFFF) "\uFFFD" else first.toChar().toString()
    }

    private fun readHex4(): Int {
        var value = 0
        repeat(4) {
            val char = text.getOrNull(position) ?: throw endOfInput()
            val digit = when (char) {
                in '0'..'9' -> char - '0'
                in 'a'..'f' -> char - 'a' + 10
                in 'A'..'F' -> char - 'A' + 10
                else -> throw unexpected()
            }
            value = value * 16 + digit
            position++
        }
        return value
    }

    private fun parseNumber(): JsonNumber {
        val start = position
        consume('-')
        if (!consume('0')) {
            if (!isDigitAt(position)) throw unexpectedOrEnd()
            skipDigits()
        }
        if (consume('.')) {
            if (!isDigitAt(position)) throw unexpectedOrEnd()
            skipDigits()
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) consume('-')
            if (!isDigitAt(position)) throw unexpectedOrEnd()
            skipDigits()
        }
        return JsonNumber(text.substring(start, position))
    }

    private fun parseLiteral(word: String, value: JsonValue): JsonValue {
        for (expected in word) {
            if (text.getOrNull(position) != expected) throw unexpectedOrEnd()
            position++
        }
        return value
    }

    private fun isDigitAt(index: Int): Boolean = text.getOrNull(index)?.let { it in '0'..'9' } == true

    private fun skipDigits() {
        while (isDigitAt(position)) position++
    }

    private fun consume(expected: Char): Boolean {
        if (text.getOrNull(position) != expected) return false
        position++
        return true
    }

    private fun skipWhitespace() {
        while (text.getOrNull(position)?.let { it == ' ' || it == '\t' || it == '\n' || it == '\r' } == true) position++
    }

    private fun endOfInput() = JsonError("JSON 이 중간에 끝났습니다")

    private fun unexpected(): JsonError {
        val line = text.take(position).count { it == '\n' } + 1
        val column = position - text.lastIndexOf('\n', position - 1)
        return JsonError("예상하지 못한 문자입니다: ${line}번째 줄 ${column}번째 글자 ${describe(text[position])}")
    }

    private fun unexpectedOrEnd(): JsonError = if (position >= text.length) endOfInput() else unexpected()

    private fun describe(char: Char): String =
        if (char.isISOControl()) "U+" + char.code.toString(16).uppercase().padStart(4, '0') else "'$char'"
}

fun JsonValue.toIndentedJson(): String = buildString { appendJson(this@toIndentedJson, depth = 0) }

private fun StringBuilder.appendJson(value: JsonValue, depth: Int) {
    when (value) {
        is JsonString -> appendJsonString(value.value)
        is JsonNumber -> append(value.literal)
        is JsonBoolean -> append(value.value)
        JsonNull -> append("null")
        is JsonArray -> appendContainer('[', ']', value.items, depth) { item -> appendJson(item, depth + 1) }
        is JsonObject -> appendContainer('{', '}', value.fields.entries, depth) { (key, item) ->
            appendJsonString(key)
            append(": ")
            appendJson(item, depth + 1)
        }
    }
}

private fun <T> StringBuilder.appendContainer(
    open: Char,
    close: Char,
    entries: Collection<T>,
    depth: Int,
    appendEntry: StringBuilder.(T) -> Unit,
) {
    append(open)
    if (entries.isEmpty()) {
        append(close)
        return
    }
    entries.forEachIndexed { index, entry ->
        if (index > 0) append(',')
        append('\n').append("  ".repeat(depth + 1))
        appendEntry(entry)
    }
    append('\n').append("  ".repeat(depth)).append(close)
}

private fun StringBuilder.appendJsonString(text: String) {
    append('"')
    for (char in text) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char < ' ') appendUnicodeEscape(char) else append(char)
        }
    }
    append('"')
}

private fun StringBuilder.appendUnicodeEscape(char: Char) {
    append("\\u").append(char.code.toString(16).padStart(4, '0'))
}
