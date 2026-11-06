class StockTools {
    var executions = 0
        private set

    fun execute(name: String, arguments: String): JsonObject {
        if (name != "stock") return json("ok" to JsonBoolean(false), "error" to text("unknown_tool"),
            "hint" to text("Use stock with a sku of tea or coffee."))
        val parsed = runCatching { parseJson(arguments).obj() }.getOrNull()
        val sku = (parsed?.fields?.get("sku") as? JsonString)?.value ?: "tea"
        executions++
        return json("ok" to JsonBoolean(true), "quantity" to number(if (sku == "tea") 7 else 11))
    }

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
