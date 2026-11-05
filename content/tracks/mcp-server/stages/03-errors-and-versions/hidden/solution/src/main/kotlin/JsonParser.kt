class JsonSyntaxException : RuntimeException()

class JsonParser(private val source: String) {
    private var position = 0

    fun parse(): JsonValue {
        val value = readValue()
        skipWhitespace()
        if (position != source.length) throw JsonSyntaxException()
        return value
    }

    private fun readValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> str(readString())
            't' -> readLiteral("true", JsonBoolean(true))
            'f' -> readLiteral("false", JsonBoolean(false))
            'n' -> readLiteral("null", JsonNull)
            '-', in '0'..'9' -> readNumber()
            else -> throw JsonSyntaxException()
        }
    }

    private fun readObject(): JsonObject {
        expect('{')
        skipWhitespace()
        val fields = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonObject(fields)
        do {
            skipWhitespace()
            val key = readString()
            skipWhitespace()
            expect(':')
            fields[key] = readValue()
            skipWhitespace()
            if (consume('}')) return JsonObject(fields)
        } while (consume(','))
        throw JsonSyntaxException()
    }

    private fun readArray(): JsonArray {
        expect('[')
        skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonArray(values)
        do {
            values += readValue()
            skipWhitespace()
            if (consume(']')) return JsonArray(values)
        } while (consume(','))
        throw JsonSyntaxException()
    }

    private fun readString(): String {
        expect('"')
        return buildString {
            while (position < source.length) {
                val character = source[position++]
                when {
                    character == '"' -> return@buildString
                    character == '\\' -> append(readEscape())
                    character < ' ' -> throw JsonSyntaxException()
                    else -> append(character)
                }
            }
            throw JsonSyntaxException()
        }
    }

    private fun readEscape(): Char = when (val escaped = take()) {
        '"', '\\', '/' -> escaped
        'b' -> '\b'
        'f' -> '\u000c'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> {
            if (position + 4 > source.length) throw JsonSyntaxException()
            val digits = source.substring(position, position + 4)
            if (digits.any { it !in "0123456789abcdefABCDEF" }) throw JsonSyntaxException()
            position += 4
            digits.toInt(16).toChar()
        }
        else -> throw JsonSyntaxException()
    }

    private fun readNumber(): JsonNumber {
        val start = position
        consume('-')
        if (!consume('0')) {
            if (peek() !in '1'..'9') throw JsonSyntaxException()
            readDigits()
        }
        if (consume('.')) readRequiredDigits()
        if (peek() == 'e' || peek() == 'E') {
            position++
            if (peek() == '+' || peek() == '-') position++
            readRequiredDigits()
        }
        return JsonNumber(source.substring(start, position))
    }

    private fun readRequiredDigits() {
        if (peek() !in '0'..'9') throw JsonSyntaxException()
        readDigits()
    }

    private fun readDigits() {
        while (peek() in '0'..'9') position++
    }

    private fun readLiteral(token: String, value: JsonValue): JsonValue {
        if (!source.startsWith(token, position)) throw JsonSyntaxException()
        position += token.length
        return value
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position] in " \t\r\n") position++
    }

    private fun expect(character: Char) {
        if (!consume(character)) throw JsonSyntaxException()
    }

    private fun consume(character: Char): Boolean {
        if (peek() != character) return false
        position++
        return true
    }

    private fun peek(): Char = source.getOrNull(position) ?: '\u0000'
    private fun take(): Char = source.getOrNull(position++) ?: throw JsonSyntaxException()
}
