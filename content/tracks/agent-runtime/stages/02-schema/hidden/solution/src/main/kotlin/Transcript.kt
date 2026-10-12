class Transcript {
    private val output = StringBuilder()

    fun block(role: Role, block: Block) = line("${role.keyword} ${describe(block)}")

    fun turnStarted(number: Int, messageCount: Int, replyLimit: Int) = line("turn $number messages=$messageCount limit=$replyLimit")

    fun stopped(reason: StopReason) = line("stop ${reason.keyword}")

    fun discarded() = line("discard")

    fun rejected(code: RejectCode) = line("reject ${code.keyword}")

    fun done(status: FinishStatus, replyText: String) = line("done ${status.keyword} ${replyText.toJsonString()}")

    fun error(error: CommandError) = line("error ${error.code}")

    fun toUtf8(): ByteArray = output.toString().encodeToByteArray()

    private fun line(text: String) {
        output.append(text).append('\n')
    }
}

private fun describe(block: Block): String = when (block) {
    is TextBlock -> "text ${block.text.toJsonString()}"
    is ToolUseBlock -> "tool ${block.id} ${block.name} ${block.input.toJson()}"
    is IncompleteToolUseBlock -> "tool ${block.id} ${block.name} incomplete"
    is ToolResultBlock -> "result ${block.toolUseId} ${if (block.isError) "error" else "ok"} ${block.content.toJsonString()}"
}

private fun String.toJsonString(): String = JsonString(this).toJson()
