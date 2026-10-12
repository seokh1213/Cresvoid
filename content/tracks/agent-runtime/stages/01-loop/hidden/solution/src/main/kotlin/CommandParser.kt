val NAME_FORMAT = Regex("[A-Za-z0-9_-]{1,64}")

private val COMMAND_READERS: Map<String, (ArgumentFields) -> Command> = mapOf(
    "tool" to ::readTool,
    "fake" to ::readFake,
    "reply" to ::readReply,
    "config" to ::readConfig,
    "reset" to { Command.Reset },
    "ask" to ::readAsk,
)

fun parseCommandLine(line: String): ParsedLine {
    val trimmed = line.trim(' ')
    if (trimmed.isEmpty()) return ParsedLine.Blank
    val readCommand = COMMAND_READERS[trimmed.substringBefore(' ')]
        ?: return ParsedLine.Invalid(CommandError.UNKNOWN_COMMAND)
    val argumentText = trimmed.substringAfter(' ', missingDelimiterValue = "").trimStart(' ')
    val arguments = parseArguments(argumentText) ?: return ParsedLine.Invalid(CommandError.BAD_JSON)
    return try {
        ParsedLine.Valid(readCommand(ArgumentFields(arguments)))
    } catch (_: BadFieldException) {
        ParsedLine.Invalid(CommandError.BAD_FIELD)
    }
}

private fun parseArguments(text: String): JsonObject? {
    if (text.isEmpty()) return JsonObject(emptyMap())
    return try {
        parseJson(text) as? JsonObject
    } catch (_: JsonSyntaxException) {
        null
    }
}

private fun readTool(arguments: ArgumentFields): Command {
    // description 은 출력에 영향을 주지 않아 종류만 확인한다
    arguments.optionalString("description")
    return Command.DefineTool(arguments.string("name"))
}

private fun readFake(arguments: ArgumentFields): Command =
    Command.SetFake(arguments.string("tool"), arguments.jsonObject("input"), arguments.string("output"))

private fun readReply(arguments: ArgumentFields): Command {
    val stopReason = StopReason.fromKeyword(arguments.string("stop")) ?: throw BadFieldException()
    val blocks = arguments.array("content").map(::readReplyBlock)
    if (!followsBlockRules(stopReason, blocks)) throw BadFieldException()
    return Command.AddReply(Reply(stopReason, blocks))
}

private fun readReplyBlock(value: JsonValue): Block {
    val block = ArgumentFields(value as? JsonObject ?: throw BadFieldException())
    return when (block.string("type")) {
        "text" -> TextBlock(block.string("text").takeUnless { it.isBlankText() } ?: throw BadFieldException())
        "tool" -> readToolUseBlock(block)
        else -> throw BadFieldException()
    }
}

private fun readToolUseBlock(block: ArgumentFields): Block {
    val id = block.identifier("id")
    val name = block.identifier("name")
    if (block.optionalBoolean("incomplete") != true) return ToolUseBlock(id, name, block.jsonObject("input"))
    if (block.has("input")) throw BadFieldException()
    return IncompleteToolUseBlock(id, name)
}

private fun followsBlockRules(stopReason: StopReason, blocks: List<Block>): Boolean {
    val incompletePositions = blocks.indices.filter { blocks[it] is IncompleteToolUseBlock }
    val incompleteOnlyAtEnd = incompletePositions.all { stopReason == StopReason.LENGTH && it == blocks.lastIndex }
    val hasToolUse = blocks.any { it is ToolUseBlock || it is IncompleteToolUseBlock }
    return blocks.isNotEmpty() && incompleteOnlyAtEnd && when (stopReason) {
        StopReason.END -> !hasToolUse
        StopReason.TOOL -> hasToolUse
        StopReason.LENGTH -> true
    }
}

private fun readConfig(arguments: ArgumentFields): Command = Command.Configure(
    SettingsChange(
        maxTurns = arguments.optionalInt("max_turns", MAX_TURNS_RANGE),
        replyLimit = arguments.optionalInt("reply_limit", REPLY_LIMIT_RANGE),
        replyLimitCap = arguments.optionalInt("reply_limit_cap", REPLY_LIMIT_RANGE),
    ),
)

private fun readAsk(arguments: ArgumentFields): Command = Command.Ask(arguments.string("text"))

private class BadFieldException : RuntimeException()

private class ArgumentFields(private val json: JsonObject) {
    fun has(field: String): Boolean = field in json.fields

    fun string(field: String): String = optionalString(field) ?: throw BadFieldException()

    fun optionalString(field: String): String? = optional<JsonString>(field)?.value

    fun identifier(field: String): String = string(field).takeIf { NAME_FORMAT.matches(it) } ?: throw BadFieldException()

    fun optionalBoolean(field: String): Boolean? = optional<JsonBoolean>(field)?.value

    fun jsonObject(field: String): JsonObject = optional<JsonObject>(field) ?: throw BadFieldException()

    fun array(field: String): List<JsonValue> = optional<JsonArray>(field)?.items ?: throw BadFieldException()

    fun optionalInt(field: String, range: IntRange): Int? =
        optional<JsonNumber>(field)?.let { it.toIntIn(range) ?: throw BadFieldException() }

    private inline fun <reified T : JsonValue> optional(field: String): T? {
        val value = json.fields[field] ?: return null
        return value as? T ?: throw BadFieldException()
    }
}
