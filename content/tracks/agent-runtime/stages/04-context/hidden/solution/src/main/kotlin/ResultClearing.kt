const val CLEARED_CONTENT = "[cleared]"

data class BlockLocation(val messageIndex: Int, val blockIndex: Int)

class ClearedResult(val location: BlockLocation, val block: ToolResultBlock)

class ClearingPlan(val tokensBefore: Long, val tokensAfter: Long, val clearedResults: List<ClearedResult>)

private class StoredResult(val location: BlockLocation, val block: ToolResultBlock, val toolName: String)

class ResultClearing(private val settings: Settings) {
    fun plan(messages: List<Message>, tokensBefore: Long): ClearingPlan? {
        if (settings.clearTrigger == 0 || tokensBefore <= settings.clearTrigger) return null
        val results = storedResultsOldestFirst(messages)
        val keptFrom = results.size - settings.clearKeep
        var tokens = tokensBefore
        val clearedResults = mutableListOf<ClearedResult>()
        for ((position, stored) in results.withIndex()) {
            if (tokens <= settings.clearTarget) break
            if (isKept(stored, isRecent = position >= keptFrom, lastMessageIndex = messages.lastIndex)) continue
            val clearedBlock = stored.block.copy(content = CLEARED_CONTENT)
            val saving = blockTokenCount(Role.USER, stored.block) - blockTokenCount(Role.USER, clearedBlock)
            if (saving <= 0) continue
            tokens -= saving
            clearedResults += ClearedResult(stored.location, clearedBlock)
        }
        return if (clearedResults.isEmpty()) null else ClearingPlan(tokensBefore, tokens, clearedResults)
    }

    private fun isKept(stored: StoredResult, isRecent: Boolean, lastMessageIndex: Int): Boolean =
        stored.block.content == CLEARED_CONTENT ||
            stored.location.messageIndex == lastMessageIndex ||
            isRecent ||
            stored.toolName in settings.clearExclude
}

private fun storedResultsOldestFirst(messages: List<Message>): List<StoredResult> =
    messages.withIndex().flatMap { (messageIndex, message) ->
        val answeredUses = messages.getOrNull(messageIndex - 1)?.toolUses.orEmpty()
        message.blocks.withIndex().mapNotNull { (blockIndex, block) ->
            (block as? ToolResultBlock)?.let { StoredResult(BlockLocation(messageIndex, blockIndex), it, answeredUses[blockIndex].name) }
        }
    }
