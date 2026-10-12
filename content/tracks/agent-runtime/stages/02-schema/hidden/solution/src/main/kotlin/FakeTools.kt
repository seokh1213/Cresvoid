data class ToolDefinition(val name: String, val inputSchema: InputSchema) {
    fun validationErrors(input: JsonObject): List<String> = when (inputSchema) {
        InputSchema.Absent -> emptyList()
        is InputSchema.Valid -> inputSchema.schema.validationErrors(input)
        InputSchema.Malformed -> error("스키마 형식이 틀린 도구가 있으면 모델이 요청을 거절하므로 도구 단계까지 오지 않는다")
    }
}

sealed interface FakeResult {
    data class Output(val text: String) : FakeResult

    data class Failure(val message: String) : FakeResult
}

class FakeTools {
    private val definitionsByName = HashMap<String, ToolDefinition>()
    private val fakes = HashMap<FakeKey, FakeResult>()

    val definitions: Collection<ToolDefinition> get() = definitionsByName.values

    fun define(name: String, schema: JsonObject?) {
        definitionsByName[name] = ToolDefinition(name, inputSchemaOf(schema))
    }

    fun setFake(tool: String, input: JsonObject, result: FakeResult) {
        fakes[FakeKey(tool, input.canonicalJson())] = result
    }

    fun run(use: ToolUseBlock): ToolResultBlock {
        val definition = definitionsByName[use.name] ?: return errorResult(use, "unknown tool: ${use.name}")
        val validationErrors = definition.validationErrors(use.input)
        if (validationErrors.isNotEmpty()) return errorResult(use, "invalid input: ${validationErrors.joinToString("; ")}")
        return when (val fake = fakes[FakeKey(use.name, use.input.canonicalJson())]) {
            null -> errorResult(use, "no result for this input")
            is FakeResult.Output -> ToolResultBlock(use.id, isError = false, content = fake.text)
            is FakeResult.Failure -> errorResult(use, fake.message)
        }
    }

    private fun errorResult(use: ToolUseBlock, content: String) = ToolResultBlock(use.id, isError = true, content = content)
}

private data class FakeKey(val tool: String, val canonicalInput: String)
