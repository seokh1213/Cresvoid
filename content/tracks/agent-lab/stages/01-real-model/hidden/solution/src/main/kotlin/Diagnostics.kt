class Diagnostics {
    private val enabled = System.getenv("LAB_DIAGNOSTICS") == "1"
    private val turns = mutableListOf<JsonValue>()

    fun record(turn: Int, choice: JsonValue, results: List<JsonValue>) {
        if (!enabled) return
        val message = choice["message"]
        val calls = (message.optional("tool_calls") as? JsonArray)?.items.orEmpty()
        val details = calls.mapIndexed { index, call ->
            val function = call["function"]
            val name = function["name"].string()
            val sku = runCatching { parseJson(function["arguments"].string())["sku"].string() }.getOrNull()
            val result = results[index]
            val validation = if (result["ok"] == JsonBoolean(true)) "accepted"
                else (result.optional("error") as? JsonString)?.value
            json(
                "name" to text(if (name == "stock") "stock" else "unknown"),
                "sku" to text(sku?.takeIf { it in setOf("tea", "coffee") } ?: "invalid"),
                "validation" to text(validation?.takeIf { it in setOf("accepted", "unknown_tool", "invalid_arguments") }
                    ?: "invalid_arguments")
            )
        }
        val reason = choice["finish_reason"].string()
        val answer = (message.optional("content") as? JsonString)?.value.orEmpty().trim()
        turns += json(
            "turn" to number(turn),
            "finish_reason" to text(reason.takeIf { it in setOf("stop", "tool_calls", "length") } ?: "other"),
            "answer_format" to text(if (calls.isNotEmpty()) "not_final" else answerFormat(answer)),
            "calls" to JsonArray(details)
        )
    }

    fun attach(result: JsonObject): JsonObject =
        if (enabled) JsonObject(result.fields + ("diagnostics" to JsonArray(turns.toList()))) else result

    private fun answerFormat(answer: String): String = when {
        answer.isEmpty() -> "empty"
        answer.matches(Regex("0|[1-9][0-9]*")) -> "canonical_integer"
        answer.matches(Regex("[+-]?[0-9]+")) -> "other_integer"
        else -> "non_integer"
    }
}
