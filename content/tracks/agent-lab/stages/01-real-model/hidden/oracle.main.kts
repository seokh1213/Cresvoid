// Keep the oracle's JSON implementation independent from the learner's reader.
class Json(private val source: String) {
    private var at = 0
    fun read(): Any? {
        val result = value()
        whitespace()
        require(at == source.length)
        return result
    }
    private fun whitespace() { while (at < source.length && source[at].isWhitespace()) at++ }
    private fun value(): Any? {
        whitespace()
        return when (source[at]) {
            '{' -> objectValue()
            '[' -> listValue()
            '"' -> stringValue()
            't' -> { require(source.startsWith("true", at)); at += 4; true }
            'f' -> { require(source.startsWith("false", at)); at += 5; false }
            'n' -> { require(source.startsWith("null", at)); at += 4; null }
            else -> {
                val start = at
                while (at < source.length && source[at] in "-+0123456789.eE") at++
                source.substring(start, at).toBigDecimal()
            }
        }
    }
    private fun objectValue(): Map<String, Any?> {
        at++
        val result = linkedMapOf<String, Any?>()
        whitespace()
        if (source[at] == '}') { at++; return result }
        while (true) {
            whitespace()
            val key = stringValue()
            whitespace(); require(source[at++] == ':')
            require(!result.containsKey(key)); result[key] = value()
            whitespace()
            if (source[at++] == '}') return result
            require(source[at - 1] == ',')
        }
    }
    private fun listValue(): List<Any?> {
        at++
        val result = mutableListOf<Any?>()
        whitespace()
        if (source[at] == ']') { at++; return result }
        while (true) {
            result += value(); whitespace()
            if (source[at++] == ']') return result
            require(source[at - 1] == ',')
        }
    }
    private fun stringValue(): String {
        require(source[at++] == '"')
        return buildString {
            while (source[at] != '"') {
                val char = source[at++]
                if (char != '\\') { append(char); continue }
                when (val escaped = source[at++]) {
                    'u' -> { append(source.substring(at, at + 4).toInt(16).toChar()); at += 4 }
                    'n' -> append('\n'); 'r' -> append('\r'); 't' -> append('\t')
                    'b' -> append('\b'); 'f' -> append('\u000c')
                    '"', '\\', '/' -> append(escaped)
                    else -> error("escape")
                }
            }
            at++
        }
    }
}
fun encode(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.map { char -> when (char) {
        '"' -> "\\\""; '\\' -> "\\\\"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"
        else -> if (char < ' ') "\\u" + char.code.toString(16).padStart(4, '0') else char.toString()
    } }.joinToString("") + "\""
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { encode(it.key.toString()) + ":" + encode(it.value) }
    is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
    else -> value.toString()
}
fun obj(value: Any?) = value as? Map<*, *> ?: error("object")
fun list(value: Any?) = value as? List<*> ?: error("array")
fun decimal(value: Any?) = value.toString().toBigDecimal()
fun stockCall(id: String, name: String = "stock", arguments: String = "{\"sku\":\"tea\"}") =
    mapOf("id" to id, "type" to "function", "function" to mapOf("name" to name, "arguments" to arguments))
fun assistant(calls: List<Any?> = emptyList(), answer: String = "7") =
    if (calls.isEmpty()) mapOf("role" to "assistant", "content" to answer)
    else mapOf("role" to "assistant", "content" to null, "tool_calls" to calls)
fun usage(input: Int, output: Int, cached: Int, cost: String) = mapOf(
    "prompt_tokens" to input, "completion_tokens" to output,
    "prompt_tokens_details" to mapOf("cached_tokens" to cached),
    "opencode" to mapOf("allowance" to cost.toBigDecimal())
)
fun scripted(prompt: String, turn: Int): Map<String, Any?> {
    val message = when (prompt) {
        "probe recovery" -> when (turn) {
            0 -> assistant(listOf(stockCall("u", "absent")))
            1 -> assistant(listOf(
                stockCall("a", arguments = "{"), stockCall("b", arguments = "{}"),
                stockCall("c", arguments = "{\"sku\":7}"),
                stockCall("d", arguments = "{\"sku\":\"tea\",\"extra\":1}"),
                stockCall("e", arguments = "{\"sku\":\"water\"}"),
                stockCall("g", arguments = "[]"), stockCall("h", arguments = "null"),
                stockCall("i", arguments = "{\"sku\":\"Tea\"}"), stockCall("f")
            ))
            else -> assistant()
        }
        "probe loop" -> assistant(listOf(stockCall("loop-$turn")))
        "probe usage" -> if (turn == 0) assistant(listOf(stockCall("coffee", arguments = "{\"sku\":\"coffee\"}")))
            else assistant(answer = "11")
        "probe limited" -> assistant(answer = "  partial  ")
        else -> assistant()
    }
    val used = if (prompt == "probe usage") {
        if (turn == 0) usage(101, 13, 17, "0.003")
        else usage(211, 29, 0, "0.007") - "prompt_tokens_details"
    } else usage(7, 3, 0, "0.001")
    val reason = if (prompt == "probe limited") "length" else if (message.containsKey("tool_calls")) "tool_calls" else "stop"
    return mapOf("choices" to listOf(mapOf("message" to (message + ("reasoning_content" to "preserve this field")),
        "finish_reason" to reason)), "usage" to (used + ("cost" to 0)))
}

fun cachedTokens(used: Map<*, *>) = decimal((used["prompt_tokens_details"] as? Map<*, *>)?.get("cached_tokens") ?: 0)

fun toolOutcome(call: Any?): Pair<String?, Int?> {
    val function = obj(obj(call)["function"])
    if (function["name"] != "stock") return "unknown_tool" to null
    val arguments = runCatching { obj(Json(function["arguments"].toString()).read()) }.getOrNull()
        ?: return "invalid_arguments" to null
    if (arguments.keys != setOf("sku") || arguments["sku"] !in setOf("tea", "coffee")) {
        return "invalid_arguments" to null
    }
    return null to if (arguments["sku"] == "tea") 7 else 11
}

fun validToolResult(call: Any?, content: Any?): Boolean {
    val result = runCatching { obj(Json(content as? String ?: return false).read()) }.getOrNull() ?: return false
    val (error, quantity) = toolOutcome(call)
    return if (error == null) result["ok"] == true && result["quantity"] == quantity!!.toBigDecimal()
    else result["ok"] == false && result["error"] == error && (result["hint"] as? String)?.isNotBlank() == true
}

fun expectedStatus(response: Map<*, *>): String {
    val choice = obj(list(response["choices"])[0])
    val calls = obj(choice["message"])["tool_calls"] as? List<*>
    return when {
        !calls.isNullOrEmpty() -> "turn_limit"
        choice["finish_reason"] == "stop" -> "completed"
        else -> "model_limit"
    }
}
