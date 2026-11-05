fun main() {
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    val engine = MatchingEngine { output.appendLine(it) }
    input.lineSequence().forEach { line ->
        val tokens = CommandParser.tokens(line)
        if (tokens.isNotEmpty()) {
            try {
                when (val command = CommandParser.parse(tokens)) {
                    is Command.Mutation -> engine.apply(command)
                    Command.Book -> output.appendLine(engine.book.render())
                }
            } catch (failure: Rejected) {
                output.appendLine("ERR ${failure.reason}")
            }
        }
    }
    output.flush()
}
