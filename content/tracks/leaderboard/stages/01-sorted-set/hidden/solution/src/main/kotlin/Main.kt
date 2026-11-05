fun main() {
    val leaderboard = Leaderboard()
    val input = System.`in`.bufferedReader()
    val output = System.out.bufferedWriter()
    input.forEachLine { raw ->
        val line = raw.trim(' ', '\t')
        if (line.isNotEmpty()) {
            val response = when (val command = parseCommand(line)) {
                is Command.Update -> if (leaderboard.update(command.user, command.value, command.kind)) null else "ERR"
                is Command.Rank -> leaderboard.rank(command.user)
                Command.Invalid -> "ERR"
            }
            if (response != null) {
                output.write(response)
                output.newLine()
            }
        }
    }
    output.flush()
}
