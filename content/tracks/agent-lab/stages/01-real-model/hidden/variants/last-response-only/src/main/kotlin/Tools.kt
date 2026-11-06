class StockTools {
    var executions = 0
        private set

    fun execute(name: String, arguments: String): JsonObject {
        if (name != "stock") return failure("unknown_tool", "Use stock with a sku of tea or coffee.")
        val parsed = runCatching { parseJson(arguments).obj() }.getOrNull()
            ?: return failure("invalid_arguments", "Send a JSON object with only sku.")
        if (parsed.fields.keys != setOf("sku")) return failure("invalid_arguments", "Send only sku.")
        val sku = (parsed.fields["sku"] as? JsonString)?.value
        if (sku !in setOf("tea", "coffee")) return failure("invalid_arguments", "sku must be tea or coffee.")
        executions++
        return json("ok" to JsonBoolean(true), "quantity" to number(if (sku == "tea") 7 else 11))
    }

    private fun failure(code: String, hint: String) = json(
        "ok" to JsonBoolean(false), "error" to text(code), "hint" to text(hint)
    )
}

fun toolDefinitions(description: String): JsonArray {
    val detail = if (description == "brief") "Look up stock." else
        "Look up current stock by product sku. Use this to answer inventory questions. " +
            "sku is the exact lowercase product identifier, either tea or coffee. " +
            "Returns quantity as an integer. Do not guess quantities or use product display names. " +
            "The tool is read only. If a call fails, correct the argument and try again."
    val parameters = json(
        "type" to text("object"),
        "properties" to json("sku" to json("type" to text("string"), "enum" to JsonArray(listOf(text("tea"), text("coffee"))))),
        "required" to JsonArray(listOf(text("sku"))), "additionalProperties" to JsonBoolean(false)
    )
    return JsonArray(listOf(json("type" to text("function"), "function" to json(
        "name" to text("stock"), "description" to text(detail), "parameters" to parameters
    ))))
}
