class Transcript {
    private val output = StringBuilder()

    fun block(role: Role, block: Block) = line(blockLine(role, block))

    fun turnStarted(number: Int, messageCount: Int, replyLimit: Int) = line("turn $number messages=$messageCount limit=$replyLimit")

    fun cleared(plan: ClearingPlan) =
        line("clear ${plan.tokensBefore} ${plan.tokensAfter} ${plan.clearedResults.joinToString(" ") { it.block.toolUseId }}")

    fun contextMeasured(tokenCount: Long) = line("context $tokenCount")

    fun stopped(reason: StopReason) = line("stop ${reason.keyword}")

    fun discarded() = line("discard")

    fun rejected(code: RejectCode) = line("reject ${code.keyword}")

    fun done(status: FinishStatus, replyText: String) = line("done ${status.keyword} ${replyText.toJsonString()}")

    fun error(error: CommandError) = line("error ${error.code}")

    fun trace(report: ToolPhaseReport) {
        report.executions.sortedWith(compareBy(Execution::endMs, Execution::order)).forEach {
            line("exec ${it.toolUseId} ${it.startMs} ${it.endMs} ${it.outcome.traceKeyword} ${it.attemptCount}")
        }
        line("clock ${report.endMs}")
    }

    fun toUtf8(): ByteArray = output.toString().encodeToByteArray()

    private fun line(text: String) {
        output.append(text).append('\n')
    }
}

fun blockLine(role: Role, block: Block): String = "${role.keyword} ${describe(block)}"

private fun describe(block: Block): String = when (block) {
    is TextBlock -> "text ${block.text.toJsonString()}"
    is ToolUseBlock -> "tool ${block.id} ${block.name} ${block.input.toJson()}"
    is IncompleteToolUseBlock -> "tool ${block.id} ${block.name} incomplete"
    is ToolResultBlock -> "result ${block.toolUseId} ${if (block.isError) "error" else "ok"} ${block.content.toJsonString()}"
}

private val AttemptOutcome.traceKeyword: String
    get() = when (this) {
        is FakeResult.Output -> "ok"
        is FakeResult.Failure -> "error"
        TimedOut -> "timeout"
    }

private fun String.toJsonString(): String = JsonString(this).toJson()
