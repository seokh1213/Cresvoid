class ErrorTally {
    private val counts = HashMap<ToolError, Int>()

    var highestCount = 0
        private set

    fun record(toolName: String, content: String) {
        val error = ToolError(toolName, content)
        val count = counts.getOrDefault(error, 0) + 1
        counts[error] = count
        highestCount = maxOf(highestCount, count)
    }
}

private data class ToolError(val toolName: String, val content: String)
