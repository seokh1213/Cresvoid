enum class StopReason(val keyword: String) {
    END("end"),
    TOOL("tool"),
    LENGTH("length"),
    ;

    companion object {
        fun fromKeyword(keyword: String): StopReason? = entries.firstOrNull { it.keyword == keyword }
    }
}

data class Reply(val stopReason: StopReason, val blocks: List<Block>) {
    val text: String get() = blocks.filterIsInstance<TextBlock>().joinToString("") { it.text }

    val toolUses: List<ToolUseBlock> get() = blocks.filterIsInstance<ToolUseBlock>()

    val isCutInsideToolUse: Boolean get() = blocks.last() is IncompleteToolUseBlock

    fun toMessage(): Message = Message(Role.MODEL, blocks)
}
