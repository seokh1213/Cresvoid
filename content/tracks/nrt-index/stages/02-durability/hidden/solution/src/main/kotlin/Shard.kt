class Shard {
    private val index = SearchIndex()
    private val translog = Translog()
    private var commit = index.commit()
    private var time = 0L
    private var lastSearch: Long? = null

    fun execute(command: Command): String = when (command) {
        is Command.Write -> write(command.mutation)
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
        is Command.Durability -> {
            translog.mode = command.mode
            "OK"
        }
        Command.Sync -> {
            translog.sync()
            "OK"
        }
        Command.Flush -> {
            flush()
            "OK"
        }
        Command.Crash -> {
            crash()
            "OK"
        }
        Command.Log -> translog.describe()
    }

    private fun write(mutation: Mutation): String {
        index.write(mutation)
        translog.append(mutation)
        return "OK"
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
            if (boundary % 5_000 == 0L) translog.sync()
            val previousSearch = lastSearch
            if (previousSearch != null && boundary - previousSearch < 30_000) index.refresh()
            boundary += 1_000
        }
        time = destination
    }

    private fun flush() {
        index.refresh()
        commit = index.commit()
        translog.newGeneration()
    }

    private fun crash() {
        index.restore(commit)
        for (mutation in translog.recover()) index.write(mutation)
        lastSearch = null
    }
}
