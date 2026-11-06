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

    fun execute(command: Command): String = when (command) {
        is Command.Add -> add(command)
        is Command.Query -> render(index.query(command.count, command.prefix))
    }

    private fun add(command: Command.Add): String {
        index.add(command.term, command.amount) ?: return "ERR"
        return "OK"
    }
}
