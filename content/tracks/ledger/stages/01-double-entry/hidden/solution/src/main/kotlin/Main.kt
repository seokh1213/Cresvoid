fun main() {
    val ledger = LedgerCommands(Ledger())
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    input.forEachLine { line ->
        val command = line.trim(' ')
        if (command.isNotEmpty()) {
            val tokens = command.split(Regex(" +"))
            val response = checkNotNull(ledger.execute(tokens)) { "Unsupported command" }
            response.forEach { output.appendLine(it) }
        }
    }
    output.flush()
}
