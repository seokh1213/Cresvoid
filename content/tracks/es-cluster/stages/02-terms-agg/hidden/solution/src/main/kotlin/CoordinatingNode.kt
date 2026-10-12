class CoordinatingNode {
    private var index: ShardedIndex? = null

    fun handle(line: RequestLine): List<String> =
        try {
            execute(line)
        } catch (failure: CommandFailure) {
            listOf("ERR ${failure.code}")
        }

    private fun execute(line: RequestLine): List<String> {
        val name = CommandName.find(line.commandName) ?: reject(ErrorCode.UNKNOWN_COMMAND)
        checkIndexState(name)
        return when (val command = parseCommand(name, line)) {
            is Command.Create -> {
                index = ShardedIndex(command.shardCount)
                emptyList()
            }
            is Command.Put -> {
                existingIndex().put(command)
                emptyList()
            }
            is Command.Delete -> {
                existingIndex().delete(command)
                emptyList()
            }
            Command.Stats -> statsLines(existingIndex())
            is Command.Search -> searchLines(search(existingIndex(), command.request))
            is Command.Terms -> termsLines(aggregateTerms(existingIndex(), command.request))
        }
    }

    private fun checkIndexState(name: CommandName) {
        val indexExists = index != null
        if (name == CommandName.CREATE && indexExists) reject(ErrorCode.INDEX_EXISTS)
        if (name != CommandName.CREATE && !indexExists) reject(ErrorCode.NO_INDEX)
    }

    private fun existingIndex(): ShardedIndex = checkNotNull(index)
}
