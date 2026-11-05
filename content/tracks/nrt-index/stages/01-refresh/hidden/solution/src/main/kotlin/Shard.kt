class Shard {
    private val index = SearchIndex()
    private var time = 0L
    private var lastSearch: Long? = null

    fun execute(command: Command): String = when (command) {
        is Command.Write -> {
            index.write(command.mutation)
            "OK"
        }
        is Command.Get -> index.get(command.id)
        is Command.Search -> search(command.word)
        is Command.Tick -> {
            tick(command.delta)
            "OK"
        }
        Command.Refresh -> {
            index.refresh()
            "OK"
        }
        Command.Merge -> {
            index.merge()
            "OK"
        }
        Command.Segments -> index.describeSegments()
        Command.State -> index.state(time)
    }

    private fun search(word: String): String {
        val previousSearch = lastSearch
        if (previousSearch == null || time - previousSearch >= 30_000) index.refresh()
        val result = index.search(word)
        lastSearch = time
        return result
    }

    private fun tick(delta: Long) {
        val destination = time + delta
        var boundary = (time / 1_000 + 1) * 1_000
        while (boundary <= destination) {
            val previousSearch = lastSearch
            if (previousSearch != null && boundary - previousSearch < 30_000) index.refresh()
            boundary += 1_000
        }
        time = destination
    }
}
