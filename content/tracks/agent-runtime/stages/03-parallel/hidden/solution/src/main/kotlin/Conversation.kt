enum class Role(val keyword: String) {
    USER("user"),
    MODEL("model"),
}

sealed interface Block

data class TextBlock(val text: String) : Block

data class ToolUseBlock(val id: String, val name: String, val input: JsonObject) : Block

data class IncompleteToolUseBlock(val id: String, val name: String) : Block

data class ToolResultBlock(val toolUseId: String, val isError: Boolean, val content: String) : Block

data class Message(val role: Role, val blocks: List<Block>) {
    val toolUses: List<ToolUseBlock> get() = blocks.filterIsInstance<ToolUseBlock>()
}

fun String.isBlankText(): Boolean = all { it == ' ' || it == '\t' || it == '\n' || it == '\r' }

class Conversation {
    private val history = mutableListOf<Message>()

    val messages: List<Message> get() = history

    fun addUserText(text: String): List<Block> {
        val last = history.lastOrNull()
        val textBlock = TextBlock(text)
        if (last?.role == Role.USER) {
            history[history.lastIndex] = last.copy(blocks = last.blocks + textBlock)
            return listOf(textBlock)
        }
        val notExecuted = last?.toolUses.orEmpty().map { ToolResultBlock(it.id, isError = true, content = "not executed") }
        val added = notExecuted + textBlock
        history += Message(Role.USER, added)
        return added
    }

    fun add(message: Message) {
        history += message
    }

    fun clear() {
        history.clear()
    }
}
