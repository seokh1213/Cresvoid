data class ToolDefinition(val name: String, val inputSchema: InputSchema, val sideEffect: Boolean, val tokenCount: Int) {
    fun validationErrors(input: JsonObject): List<String> = when (inputSchema) {
        InputSchema.Absent -> emptyList()
        is InputSchema.Valid -> inputSchema.schema.validationErrors(input)
        InputSchema.Malformed -> error("스키마 형식이 틀린 도구가 있으면 모델이 요청을 거절하므로 도구 단계까지 오지 않는다")
    }
}

sealed interface FakeResult : AttemptOutcome {
    data class Output(val text: String) : FakeResult

    data class Failure(val message: String) : FakeResult
}

data class Attempt(val result: FakeResult, val durationMs: Int)

sealed interface ToolCall {
    data class Skipped(val result: ToolResultBlock) : ToolCall

    data class Runnable(val use: ToolUseBlock, val attempts: List<Attempt>, val sideEffect: Boolean) : ToolCall
}

class FakeTools {
    private val definitionsByName = HashMap<String, ToolDefinition>()
    private val fakes = HashMap<FakeKey, List<Attempt>>()

    val definitions: Collection<ToolDefinition> get() = definitionsByName.values

    fun define(name: String, schema: JsonObject?, sideEffect: Boolean, arguments: JsonObject) {
        definitionsByName[name] = ToolDefinition(name, inputSchemaOf(schema), sideEffect, tokenCountOf(arguments.toJson()))
    }

    fun setFake(tool: String, input: JsonObject, attempts: List<Attempt>) {
        fakes[FakeKey(tool, input.canonicalJson())] = attempts
    }

    fun prepare(use: ToolUseBlock): ToolCall {
        val definition = definitionsByName[use.name] ?: return skipped(use, "unknown tool: ${use.name}")
        val validationErrors = definition.validationErrors(use.input)
        if (validationErrors.isNotEmpty()) return skipped(use, "invalid input: ${validationErrors.joinToString("; ")}")
        val attempts = fakes[FakeKey(use.name, use.input.canonicalJson())] ?: return skipped(use, "no result for this input")
        return ToolCall.Runnable(use, attempts, definition.sideEffect)
    }

    private fun skipped(use: ToolUseBlock, content: String) =
        ToolCall.Skipped(ToolResultBlock(use.id, isError = true, content = content))
}

private data class FakeKey(val tool: String, val canonicalInput: String)
