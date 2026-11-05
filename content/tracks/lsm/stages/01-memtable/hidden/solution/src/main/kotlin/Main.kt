fun main() {
    val output = System.out.bufferedWriter()
    val session = Session()
    System.`in`.bufferedReader().useLines { lines ->
        for (line in lines) {
            val command = parseCommand(line) ?: continue
            for (row in session.execute(command)) {
                output.appendLine(row)
            }
        }
    }
    output.flush()
}

private class Session {
    private var store: Store? = null

    fun execute(command: Command): List<String> {
        if (command is Command.Reset) {
            store = Store(command.capacity)
            return listOf("OK")
        }
        val current = store ?: return listOf("ERROR")
        return when (command) {
            is Command.Put -> {
                current.write(command.key, Payload.Value(command.value))
                listOf("OK")
            }
            is Command.Delete -> {
                current.write(command.key, Payload.Tombstone)
                listOf("OK")
            }
            is Command.Get -> listOf(current.get(command.key).lookupOutput())
            Command.Flush -> {
                current.flush()
                listOf("OK")
            }
            Command.Layout -> current.layout()
            Command.Invalid -> listOf("ERROR")
            is Command.Reset -> error("RESET is handled before accessing a store")
        }
    }
}
