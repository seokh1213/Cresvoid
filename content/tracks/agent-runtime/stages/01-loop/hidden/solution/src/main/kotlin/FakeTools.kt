class FakeTools {
    private val definedNames = mutableSetOf<String>()
    private val outputs = HashMap<FakeKey, String>()

    val toolNames: Set<String> get() = definedNames

    fun define(name: String) {
        definedNames += name
    }

    fun setFake(tool: String, input: JsonObject, output: String) {
        outputs[FakeKey(tool, input.canonicalJson())] = output
    }

    fun run(use: ToolUseBlock): ToolResultBlock {
        if (use.name !in definedNames) return errorResult(use, "unknown tool: ${use.name}")
        val output = outputs[FakeKey(use.name, use.input.canonicalJson())]
            ?: return errorResult(use, "no result for this input")
        return ToolResultBlock(use.id, isError = false, content = output)
    }

    private fun errorResult(use: ToolUseBlock, content: String) = ToolResultBlock(use.id, isError = true, content = content)
}

private data class FakeKey(val tool: String, val canonicalInput: String)
