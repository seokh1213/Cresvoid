sealed interface JsonValue

data class JsonString(val value: String) : JsonValue

data class JsonNumber(val literal: String) : JsonValue

data class JsonBoolean(val value: Boolean) : JsonValue

data object JsonNull : JsonValue

data class JsonArray(val items: List<JsonValue>) : JsonValue

data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue

class JsonSyntaxException(message: String) : RuntimeException(message)

fun parseJson(text: String): JsonValue = JsonReader(text).readDocument()

fun JsonValue.toJson(): String = buildString { appendJson(this@toJson) }

private class JsonReader(private val text: String) {
    private var position = 0

    fun readDocument(): JsonValue {
        skipWhitespace()
        val value = readValue()
        skipWhitespace()
        if (position < text.length) throw syntaxError("값 뒤에 다른 글자가 있다")
        return value
    }

    private fun readValue(): JsonValue = when (text.getOrNull(position)) {
        '{' -> readObject()
        '[' -> readArray()
        '"' -> JsonString(readString())
        't' -> readLiteral("true", JsonBoolean(true))
        'f' -> readLiteral("false", JsonBoolean(false))
        'n' -> readLiteral("null", JsonNull)
        null -> throw syntaxError("값이 없다")
        else -> readNumber()
    }

    private fun readObject(): JsonObject {
        position++
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (consume('}')) return JsonObject(fields)
        while (true) {
            skipWhitespace()
            if (text.getOrNull(position) != '"') throw syntaxError("필드 이름이 와야 한다")
            val name = readString()
            if (name in fields) throw syntaxError("필드 이름이 겹친다: $name")
            skipWhitespace()
            if (!consume(':')) throw syntaxError("':' 가 와야 한다")
            skipWhitespace()
            fields[name] = readValue()
            skipWhitespace()
            if (consume('}')) return JsonObject(fields)
            if (!consume(',')) throw syntaxError("',' 나 '}' 가 와야 한다")
        }
    }

    private fun readArray(): JsonArray {
        position++
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (consume(']')) return JsonArray(items)
        while (true) {
            skipWhitespace()
            items += readValue()
            skipWhitespace()
            if (consume(']')) return JsonArray(items)
            if (!consume(',')) throw syntaxError("',' 나 ']' 가 와야 한다")
        }
    }

    private fun readString(): String {
        position++
        val builder = StringBuilder()
        while (true) {
            val char = text.getOrNull(position) ?: throw syntaxError("문자열이 닫히지 않았다")
            position++
            when {
                char == '"' -> return builder.toString()
                char == '\\' -> builder.append(readEscape())
                char < ' ' -> throw syntaxError("문자열 안에 제어 문자가 있다")
                else -> builder.append(char)
            }
        }
    }

    private fun readEscape(): String {
        val escape = text.getOrNull(position) ?: throw syntaxError("이스케이프가 끝나지 않았다")
        position++
        return when (escape) {
            '"' -> "\""
            '\\' -> "\\"
            '/' -> "/"
            'b' -> "\b"
            'f' -> "\u000C"
            'n' -> "\n"
            'r' -> "\r"
            't' -> "\t"
            'u' -> readUnicodeEscape()
            else -> throw syntaxError("모르는 이스케이프다: \\$escape")
        }
    }

    private fun readUnicodeEscape(): String {
        val first = readHex4()
        if (first.isLowSurrogate()) throw syntaxError("짝이 없는 서로게이트다")
        if (!first.isHighSurrogate()) return first.toString()
        if (!text.startsWith("\\u", position)) throw syntaxError("짝이 없는 서로게이트다")
        position += 2
        val second = readHex4()
        if (!second.isLowSurrogate()) throw syntaxError("짝이 없는 서로게이트다")
        return "$first$second"
    }

    private fun readHex4(): Char {
        val digits = text.substring(position, minOf(position + 4, text.length))
        val code = digits.takeIf { it.length == 4 && it.all(::isHexDigit) }?.toInt(16)
            ?: throw syntaxError("\\u 뒤에는 16진수 네 자리가 와야 한다")
        position += 4
        return code.toChar()
    }

    private fun readNumber(): JsonNumber {
        val match = NUMBER.matchAt(text, position) ?: throw syntaxError("값이 와야 한다")
        position = match.range.last + 1
        return JsonNumber(match.value)
    }

    private fun readLiteral(word: String, value: JsonValue): JsonValue {
        if (!text.startsWith(word, position)) throw syntaxError("값이 와야 한다")
        position += word.length
        return value
    }

    private fun consume(expected: Char): Boolean {
        if (text.getOrNull(position) != expected) return false
        position++
        return true
    }

    private fun skipWhitespace() {
        while (text.getOrNull(position)?.let { it == ' ' || it == '\t' || it == '\n' || it == '\r' } == true) position++
    }

    private fun syntaxError(reason: String) = JsonSyntaxException("${position}번째 글자 근처: $reason")
}

// RFC 8259 의 number 문법이라 01, 1., .5, 1e 는 맞지 않는다
private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

private fun isHexDigit(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

private fun StringBuilder.appendJson(value: JsonValue) {
    when (value) {
        is JsonString -> appendJsonString(value.value)
        is JsonNumber -> append(value.literal)
        is JsonBoolean -> append(value.value)
        JsonNull -> append("null")
        is JsonArray -> {
            append('[')
            value.items.forEachIndexed { index, item ->
                if (index > 0) append(',')
                appendJson(item)
            }
            append(']')
        }
        is JsonObject -> {
            append('{')
            value.fields.entries.forEachIndexed { index, (name, item) ->
                if (index > 0) append(',')
                appendJsonString(name)
                append(':')
                appendJson(item)
            }
            append('}')
        }
    }
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
            else -> if (char < ' ') append("\\u").append(char.code.toString(16).padStart(4, '0')) else append(char)
        }
    }
    append('"')
}
