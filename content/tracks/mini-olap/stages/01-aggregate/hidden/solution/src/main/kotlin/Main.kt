fun main() {
    val output = System.out.bufferedWriter()
    val database = Database { line -> output.append(line).append('\n') }
    for (line in System.`in`.readBytes().decodeToString().split('\n')) {
        val command = line.trim(' ')
        if (command.isNotEmpty()) database.execute(command)
    }
    output.flush()
}
