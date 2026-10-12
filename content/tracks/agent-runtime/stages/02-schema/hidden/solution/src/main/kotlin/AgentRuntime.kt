enum class FinishStatus(val keyword: String) {
    END("end"),
    MAX_TURNS("max_turns"),
    LENGTH("length"),
    REJECTED("rejected"),
    REPEATED_ERROR("repeated_error"),
}

private data class Turn(val number: Int, val replyLimit: Int)

private sealed interface TurnOutcome {
    data class Finished(val status: FinishStatus, val text: String) : TurnOutcome

    data class NextTurn(val replyLimit: Int) : TurnOutcome
}

class AgentRuntime(private val transcript: Transcript) {
    private val tools = FakeTools()
    private val model = ScriptedModel()
    private val conversation = Conversation()
    private var settings = Settings()

    fun execute(command: Command) {
        when (command) {
            is Command.DefineTool -> tools.define(command.name, command.schema)
            is Command.SetFake -> tools.setFake(command.tool, command.input, command.result)
            is Command.AddReply -> model.append(command.reply)
            is Command.Configure -> configure(command.change)
            Command.Reset -> conversation.clear()
            is Command.Ask -> ask(command.text)
        }
    }

    private fun configure(change: SettingsChange) {
        val updated = settings.with(change)
        if (updated.isConsistent) settings = updated else transcript.error(CommandError.BAD_FIELD)
    }

    private fun ask(text: String) {
        conversation.addUserText(text).forEach { transcript.block(Role.USER, it) }
        val errors = ErrorTally()
        var turn = Turn(number = 1, replyLimit = settings.replyLimit)
        while (true) {
            when (val outcome = playTurn(turn, errors)) {
                is TurnOutcome.Finished -> {
                    transcript.done(outcome.status, outcome.text)
                    return
                }
                is TurnOutcome.NextTurn -> turn = Turn(turn.number + 1, outcome.replyLimit)
            }
        }
    }

    private fun playTurn(turn: Turn, errors: ErrorTally): TurnOutcome {
        transcript.turnStarted(turn.number, conversation.messages.size, turn.replyLimit)
        val reply = when (val response = model.respond(ModelRequest(tools.definitions, conversation.messages))) {
            is ModelResponse.Rejected -> return reject(response.code)
            is ModelResponse.Replied -> response.reply
        }
        reply.blocks.forEach { transcript.block(Role.MODEL, it) }
        transcript.stopped(reply.stopReason)
        return when (reply.stopReason) {
            StopReason.END -> keepAndFinish(reply, FinishStatus.END)
            StopReason.TOOL -> keepAndRunTools(reply, turn, errors)
            StopReason.LENGTH ->
                if (reply.isCutInsideToolUse) discard(reply, turn) else keepAndFinish(reply, FinishStatus.LENGTH)
        }
    }

    private fun reject(code: RejectCode): TurnOutcome {
        transcript.rejected(code)
        conversation.clear()
        return TurnOutcome.Finished(FinishStatus.REJECTED, "")
    }

    private fun keepAndFinish(reply: Reply, status: FinishStatus): TurnOutcome {
        conversation.add(reply.toMessage())
        return TurnOutcome.Finished(status, reply.text)
    }

    private fun keepAndRunTools(reply: Reply, turn: Turn, errors: ErrorTally): TurnOutcome {
        conversation.add(reply.toMessage())
        if (turn.number == settings.maxTurns) return TurnOutcome.Finished(FinishStatus.MAX_TURNS, reply.text)
        runToolPhase(reply.toolUses, errors)
        if (settings.repeatLimit >= 1 && errors.highestCount >= settings.repeatLimit) {
            return TurnOutcome.Finished(FinishStatus.REPEATED_ERROR, reply.text)
        }
        return TurnOutcome.NextTurn(turn.replyLimit)
    }

    private fun discard(reply: Reply, turn: Turn): TurnOutcome {
        transcript.discarded()
        return when {
            turn.replyLimit == settings.replyLimitCap -> TurnOutcome.Finished(FinishStatus.LENGTH, reply.text)
            turn.number == settings.maxTurns -> TurnOutcome.Finished(FinishStatus.MAX_TURNS, reply.text)
            else -> TurnOutcome.NextTurn(minOf(turn.replyLimit * 2, settings.replyLimitCap))
        }
    }

    private fun runToolPhase(toolUses: List<ToolUseBlock>, errors: ErrorTally) {
        val results = toolUses.map(tools::run)
        conversation.add(Message(Role.USER, results))
        results.forEach { transcript.block(Role.USER, it) }
        toolUses.zip(results)
            .filter { (_, result) -> result.isError }
            .forEach { (use, result) -> errors.record(use.name, result.content) }
    }
}
