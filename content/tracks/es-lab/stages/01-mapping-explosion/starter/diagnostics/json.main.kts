
// The measurement decoder is independent of the learner's JSON implementation.
class JsonReader(private val text: String) {
    private var offset = 0
    fun readDocument(): Any? {
        val value = read()
        skip()
        check(offset == text.length) { "Trailing JSON content" }
        return value
    }
    private fun read(): Any? {
        skip()
        return when (text[offset]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            else -> readScalar()
        }
    }
    private fun readObject(): Map<String, Any?> {
        offset++
        val fields = linkedMapOf<String, Any?>()
        skip()
        if (text[offset] == '}') { offset++; return fields }
        while (true) {
            skip()
            val key = readString()
            check(key !in fields) { "Duplicate JSON key" }
            skip()
            check(text[offset++] == ':')
            fields[key] = read()
            skip()
            if (text[offset] == ',') offset++ else { check(text[offset++] == '}'); return fields }
        }
    }
    private fun readArray(): List<Any?> {
        offset++
        val values = mutableListOf<Any?>()
        skip()
        if (text[offset] == ']') { offset++; return values }
        while (true) {
            values += read()
            skip()
            if (text[offset] == ',') offset++ else { check(text[offset++] == ']'); return values }
        }
    }
    private fun readString(): String = buildString {
        check(text[offset++] == '"')
        while (true) {
            val char = text[offset++]
            if (char == '"') break
            check(char >= ' ') { "Unescaped JSON control character" }
            if (char != '\\') { append(char); continue }
            val escaped = text[offset++]
            append(when (escaped) {
                'u' -> readUnicode()
                'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'; 'f' -> '\u000C'
                '"', '\\', '/' -> escaped
                else -> error("Invalid JSON escape")
            })
        }
    }
    private fun readUnicode(): Char {
        val digits = text.substring(offset, offset + 4)
        check(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
        offset += 4
        return digits.toInt(16).toChar()
    }
    private fun readScalar(): Any? {
        val start = offset
        while (text.getOrNull(offset)?.let { !it.isWhitespace() && it !in ",]}" } == true) offset++
        return when (val token = text.substring(start, offset)) {
            "true" -> true; "false" -> false; "null" -> null
            else -> {
                check(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(token))
                token.toDouble()
            }
        }
    }
    private fun skip() { while (text.getOrNull(offset)?.let { it in " \t\r\n" } == true) offset++ }
}

fun obj(value: Any?): Map<*, *> = value as? Map<*, *> ?: error("Expected JSON object")
fun items(value: Any?): List<*> = value as? List<*> ?: error("Expected JSON array")
fun decode(text: String): Map<*, *> = obj(JsonReader(text).readDocument())
fun quote(text: String): String = buildString {
    append('"')
    for (char in text) when (char) {
        '"' -> append("\\\""); '\\' -> append("\\\\")
        else -> if (char < ' ') append("\\u${char.code.toString(16).padStart(4, '0')}") else append(char)
    }
    append('"')
}
fun encode(fields: Map<String, String>): String = fields.entries.joinToString(",", "{", "}") { (key, value) ->
    "${quote(key)}:${quote(value)}"
}
