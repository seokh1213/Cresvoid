fun main() {
    val parser = CommandParser()
    val shard = Shard()
    val output = System.out.bufferedWriter()
    System.`in`.bufferedReader().useLines { lines ->
        for (rawLine in lines) {
            val line = rawLine.trim(' ', '\t')
            if (line.isEmpty()) continue
            val command = parser.parse(line)
            output.appendLine(if (command == null) "ERR INPUT" else shard.execute(command))
        }
    }
    output.flush()
}
