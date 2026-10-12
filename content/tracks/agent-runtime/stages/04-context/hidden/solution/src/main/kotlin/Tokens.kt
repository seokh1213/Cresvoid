private const val CHARACTERS_PER_TOKEN = 4

fun tokenCountOf(text: String): Int = Math.ceilDiv(text.codePointCount(0, text.length), CHARACTERS_PER_TOKEN)

fun blockTokenCount(role: Role, block: Block): Int = tokenCountOf(blockLine(role, block))

fun requestTokenCount(tools: Collection<ToolDefinition>, messages: List<Message>): Long =
    tools.sumOf { it.tokenCount.toLong() } + messages.sumOf { it.tokenCount }
