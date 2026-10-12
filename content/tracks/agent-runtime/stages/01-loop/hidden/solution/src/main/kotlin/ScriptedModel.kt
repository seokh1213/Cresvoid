enum class RejectCode(val keyword: String) {
    TOOL_NAME("tool_name"),
    ROLE_ORDER("role_order"),
    EMPTY_CONTENT("empty_content"),
    DUPLICATE_ID("duplicate_id"),
    PAIRING("pairing"),
    SCRIPT_EXHAUSTED("script_exhausted"),
}

class ModelRequest(val toolNames: Collection<String>, val messages: List<Message>)

sealed interface ModelResponse {
    data class Replied(val reply: Reply) : ModelResponse

    data class Rejected(val code: RejectCode) : ModelResponse
}

class ScriptedModel {
    private val script = ArrayDeque<Reply>()

    fun append(reply: Reply) {
        script.addLast(reply)
    }

    fun respond(request: ModelRequest): ModelResponse {
        val violation = findViolation(request)
        return when {
            violation != null -> ModelResponse.Rejected(violation)
            script.isEmpty() -> ModelResponse.Rejected(RejectCode.SCRIPT_EXHAUSTED)
            else -> ModelResponse.Replied(script.removeFirst())
        }
    }
}

private fun findViolation(request: ModelRequest): RejectCode? = when {
    request.toolNames.any { !NAME_FORMAT.matches(it) } -> RejectCode.TOOL_NAME
    !rolesAlternate(request.messages) -> RejectCode.ROLE_ORDER
    request.messages.any(::hasEmptyContent) -> RejectCode.EMPTY_CONTENT
    hasDuplicateToolUseId(request.messages) -> RejectCode.DUPLICATE_ID
    !resultsPairWithToolUses(request.messages) -> RejectCode.PAIRING
    else -> null
}

private fun rolesAlternate(messages: List<Message>): Boolean =
    messages.firstOrNull()?.role == Role.USER &&
        messages.last().role == Role.USER &&
        messages.zipWithNext().all { (previous, next) -> previous.role != next.role }

private fun hasEmptyContent(message: Message): Boolean =
    message.blocks.isEmpty() || message.blocks.any { it is TextBlock && it.text.isBlankText() }

private fun hasDuplicateToolUseId(messages: List<Message>): Boolean {
    val ids = messages.flatMap { message -> message.toolUses.map { it.id } }
    return ids.size != ids.toSet().size
}

private fun resultsPairWithToolUses(messages: List<Message>): Boolean =
    messages.indices.filter { messages[it].role == Role.USER }.all { index ->
        val expectedIds = messages.getOrNull(index - 1)?.toolUses.orEmpty().map { it.id }
        val blocks = messages[index].blocks
        val leadingIds = blocks.take(expectedIds.size).map { (it as? ToolResultBlock)?.toolUseId }
        leadingIds == expectedIds && blocks.drop(expectedIds.size).none { it is ToolResultBlock }
    }
