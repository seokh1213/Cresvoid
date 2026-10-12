enum class CommandName {
    CREATE,
    PUT,
    DEL,
    STATS,
    SEARCH,
    TERMS,
    ;

    companion object {
        fun find(word: String): CommandName? = entries.firstOrNull { it.name == word }
    }
}

sealed interface Command {
    data class Create(val shardCount: Int) : Command

    data class Put(val id: Int, val routing: String, val brand: String, val price: Int, val title: String) : Command

    data class Delete(val id: Int, val routing: String) : Command

    data object Stats : Command

    data class Search(val request: SearchRequest) : Command

    data class Terms(val request: TermsRequest) : Command
}
