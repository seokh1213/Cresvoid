fun main() {
    val application = Autocomplete()
    val output = System.out.bufferedWriter(Charsets.UTF_8)
    System.`in`.bufferedReader(Charsets.UTF_8).forEachLine { line ->
        if (line.any { it != ' ' && it != '\t' }) {
            val command = CommandParser.parse(line)
            output.appendLine(if (command == null) "ERR" else application.execute(command))
        }
    }
    output.flush()
}

class Autocomplete {
    private val index = SearchIndex()
    private var cache: RankingCache? = null

    fun execute(command: Command): String = when (command) {
        is Command.Add -> add(command)
        is Command.Query -> render(index.query(command.count, command.prefix))
        is Command.Cache -> {
            cache = RankingCache(index, command.mode, command.capacity)
            "OK"
        }
        is Command.CachedQuery -> cache?.query(command.count, command.prefix) ?: "ERR"
        Command.Refresh -> cache?.let { it.refresh(); "OK" } ?: "ERR"
        Command.Cost -> cache?.cost() ?: "ERR"
        is Command.Ban -> { index.ban(command.term); "OK" }
        is Command.Unban -> { index.unban(command.term); "OK" }
        is Command.Shards -> { index.layout.repartition(command.count); "OK" }
        is Command.Route -> index.layout.route(command.prefix)
        Command.Layout -> index.layout.render()
        is Command.FilteredQuery -> render(index.filteredQuery(command.count, command.prefix))
    }

    private fun add(command: Command.Add): String {
        val path = index.add(command.term, command.amount) ?: return "ERR"
        cache?.afterAdd(command.term, path)
        return "OK"
    }
}
