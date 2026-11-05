enum class Prompt(val wireName: String, val description: String, val arguments: List<PromptArgument>) {
    COMPARE("compare_products", "두 상품의 차이를 비교한다.", listOf(
        PromptArgument("leftId", true), PromptArgument("rightId", true),
    )),
    INSPECT("inspect_product", "상품에 대한 질문을 작성한다.", listOf(
        PromptArgument("productId", true), PromptArgument("question", false),
    )),
    SUMMARIZE("summarize_catalog", "카탈로그 요약을 요청한다.", emptyList()),
}

data class PromptArgument(val name: String, val required: Boolean)

object Prompts {
    fun list(params: JsonObject): JsonObject {
        return paginatedList("prompts", params, Prompt.entries.map(::descriptor))
    }

    fun get(params: JsonObject): JsonObject {
        val name = params["name"].requireString()
        val prompt = Prompt.entries.firstOrNull { it.wireName == name } ?: invalidParams()
        val arguments = if (params.contains("arguments")) params["arguments"].requireObject() else obj()
        validateArguments(prompt, arguments)
        return obj(
            "resultType" to str("complete"), "description" to str(prompt.description),
            "messages" to JsonArray(messages(prompt, arguments)),
        )
    }

    private fun validateArguments(prompt: Prompt, arguments: JsonObject) {
        val allowed = prompt.arguments.map { it.name }.toSet()
        if (arguments.fields.keys.any { it !in allowed }) invalidParams()
        if (arguments.fields.values.any { it !is JsonString }) invalidParams()
        if (prompt.arguments.any { it.required && !arguments.contains(it.name) }) invalidParams()
        for ((name, value) in arguments.fields) {
            if (name != "question" && value.requireString() !in Catalog.byId) invalidParams()
        }
    }

    private fun messages(prompt: Prompt, arguments: JsonObject): List<JsonObject> = when (prompt) {
        Prompt.COMPARE -> listOf(
            textMessage("두 상품을 비교하라."),
            resourceMessage(arguments["leftId"].requireString()),
            resourceMessage(arguments["rightId"].requireString()),
        )
        Prompt.INSPECT -> listOf(
            textMessage(if (arguments.contains("question")) arguments["question"].requireString()
                else "구매 전에 확인할 점을 설명하라."),
            resourceMessage(arguments["productId"].requireString()),
        )
        Prompt.SUMMARIZE -> listOf(textMessage(summary()))
    }

    private fun summary(): String {
        val products = Catalog.products
        return "카탈로그를 요약하라.\nproducts=${products.size}\navailable=${products.count { it.stock > 0 }}\nstock=${products.sumOf { it.stock }}"
    }

    private fun textMessage(text: String): JsonObject = message(obj("type" to str("text"), "text" to str(text)))

    private fun resourceMessage(id: String): JsonObject = message(obj(
        "type" to str("resource"), "resource" to Resources.contents(Catalog.byId.getValue(id)),
    ))

    private fun message(content: JsonObject): JsonObject = obj("role" to str("user"), "content" to content)

    private fun descriptor(prompt: Prompt): JsonObject = obj(
        "name" to str(prompt.wireName), "description" to str(prompt.description),
        "arguments" to JsonArray(prompt.arguments.map { argument ->
            obj("name" to str(argument.name), "required" to JsonBoolean(argument.required))
        }),
    )
}
