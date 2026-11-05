fun main() {
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    val session = ReplaySession(input) { output.appendLine(it) }
    input.lineSequence().forEach { line ->
        val tokens = CommandParser.tokens(line)
        if (tokens.isNotEmpty()) {
            try {
                session.accept(tokens)
            } catch (failure: Rejected) {
                output.appendLine("ERR ${failure.reason}")
            }
        }
    }
    output.flush()
}
